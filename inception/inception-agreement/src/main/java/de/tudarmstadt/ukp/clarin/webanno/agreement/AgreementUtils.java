/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.agreement;

import static de.tudarmstadt.ukp.inception.support.uima.WebAnnoCasUtil.getFeature;
import static org.apache.uima.fit.util.FSUtil.getFeature;

import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.uima.cas.ArrayFS;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.dkpro.statistics.agreement.coding.CodingAnnotationStudy;
import org.dkpro.statistics.agreement.coding.ICodingAnnotationStudy;

import de.tudarmstadt.ukp.clarin.webanno.agreement.results.coding.FullCodingAgreementResult;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.Configuration;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.ConfigurationSet;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.LinkCompareBehavior;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.Tag;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.relation.RelationDiffAdapter;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.relation.RelationPosition;

public class AgreementUtils
{
    public static FullCodingAgreementResult makeCodingStudy(CasDiff aDiff, String aType,
            String aFeature, Set<String> aTagSet, boolean aExcludeIncomplete,
            Map<String, CAS> aCasMap)
    {
        return makeCodingStudy(aDiff, aCasMap.keySet(), aType, aFeature, aTagSet,
                aExcludeIncomplete, true, aCasMap);
    }

    private static CAS findSomeCas(Map<String, CAS> aCasMap)
    {
        for (var cas : aCasMap.values()) {
            if (cas != null) {
                return cas;
            }
        }

        return null;
    }

    private static FullCodingAgreementResult makeCodingStudy(CasDiff aDiff,
            Collection<String> aUsers, String aType, String aFeature, Set<String> aTagSet,
            boolean aExcludeIncomplete, boolean aNullLabelsAsEmpty, Map<String, CAS> aCasMap)
    {
        var users = aUsers.stream().sorted().toList();

        var study = new CodingAnnotationStudy(users.size());

        if (aTagSet != null) {
            aTagSet.forEach(study::addCategory);
        }

        // Check if the feature we are looking at is a primitive feature or a link feature
        // We do this by looking it up in the first available CAS. Mind that at this point all
        // CASes should have exactly the same typesystem.
        var someCas = findSomeCas(aCasMap);
        if (someCas == null) {
            // Well... there is NOTHING here!
            // All positions are irrelevant
            var irrelevantSets = aDiff.getPositions().stream() //
                    .map(aDiff::getConfigurationSet) //
                    .map(s -> s.addTags(Tag.IRRELEVANT)) //
                    .toList();

            return new FullCodingAgreementResult(aType, aFeature, aDiff.toResult(), study, users,
                    irrelevantSets, aExcludeIncomplete);
        }

        var ts = someCas.getTypeSystem();

        // This happens in our test cases when we feed the process with uninitialized CASes.
        // We should just do the right thing here which is: do nothing
        if (ts.getType(aType) == null) {
            // All positions are irrelevant
            var irrelevantSets = aDiff.getPositions().stream() //
                    .map(aDiff::getConfigurationSet) //
                    .map(s -> s.addTags(Tag.IRRELEVANT)) //
                    .toList();

            return new FullCodingAgreementResult(aType, aFeature, aDiff.toResult(), study, users,
                    irrelevantSets, aExcludeIncomplete);
        }

        // Check that the feature really exists instead of just getting a NPE later
        if (ts.getType(aType).getFeatureByBaseName(aFeature) == null) {
            throw new IllegalArgumentException(
                    "Type [" + aType + "] has no feature called [" + aFeature + "]");
        }

        var isPrimitiveFeature = ts.getType(aType).getFeatureByBaseName(aFeature).getRange()
                .isPrimitive();

        nextPosition: for (var p : aDiff.getPositions()) {
            var cfgSet = aDiff.getConfigurationSet(p);

            // Only calculate agreement for the given layer
            if (!cfgSet.getPosition().getType().equals(aType)) {
                // We don't even consider these as irrelevant, they are just filtered out
                continue;
            }

            // If the feature on a position is set, then it is a subposition
            var isSubPosition = p.getFeature() != null;

            // Check if this position is irrelevant:
            // - if we are looking for a primitive type and encounter a subposition
            // - if we are looking for a non-primitive type and encounter a primary position
            // this is an inverted XOR!
            if (!(isPrimitiveFeature ^ isSubPosition)) {
                cfgSet.addTags(Tag.IRRELEVANT);
                continue;
            }

            // Check if subposition is for the feature we are looking for or for a different
            // feature
            if (isSubPosition && !aFeature.equals(cfgSet.getPosition().getFeature())) {
                cfgSet.addTags(Tag.IRRELEVANT);
                continue nextPosition;
            }

            // If none of the current users has made any annotation at this position, then skip it
            if (users.stream().filter(u -> cfgSet.getCasGroupIds().contains(u)).count() == 0) {
                cfgSet.addTags(Tag.IRRELEVANT);
                continue nextPosition;
            }

            var values = new Object[users.size()];
            var i = 0;
            for (var user : users) {
                // Set has to include all users, otherwise we cannot calculate the agreement for
                // this configuration set.
                if (!cfgSet.getCasGroupIds().contains(user)) {
                    cfgSet.addTags(Tag.INCOMPLETE_POSITION);
                    if (aExcludeIncomplete) {
                        // Record as incomplete
                        continue nextPosition;
                    }
                    else {
                        // Record as missing value
                        values[i] = null;
                        i++;
                        continue;
                    }
                }

                // Make sure a single user didn't do multiple alternative annotations at a single
                // position. So there is currently no support for calculating agreement on stacking
                // annotations.
                var cfgs = cfgSet.getConfigurations(user);
                if (cfgs.size() > 1) {
                    cfgSet.addTags(Tag.STACKED);
                    continue nextPosition;
                }

                Configuration cfg = cfgs.get(0);

                // Check if source and/or targets of a relation are stacked
                if (cfg.getPosition() instanceof RelationPosition pos) {
                    var arc = cfg.getFs(user, aCasMap);

                    var adapter = (RelationDiffAdapter) aDiff.getTypeAdapters().get(pos.getType());

                    // Check if the source of the relation is stacked
                    var source = getFeature(arc, adapter.getSourceFeature(), AnnotationFS.class);
                    List<AnnotationFS> sourceCandidates = CasUtil.selectAt(arc.getCAS(),
                            source.getType(), source.getBegin(), source.getEnd());
                    if (sourceCandidates.size() > 1) {
                        cfgSet.addTags(Tag.STACKED);
                        continue nextPosition;
                    }

                    // Check if the target of the relation is stacked
                    var target = getFeature(arc, adapter.getTargetFeature(), AnnotationFS.class);
                    List<AnnotationFS> targetCandidates = CasUtil.selectAt(arc.getCAS(),
                            target.getType(), target.getBegin(), target.getEnd());
                    if (targetCandidates.size() > 1) {
                        cfgSet.addTags(Tag.STACKED);
                        continue nextPosition;
                    }
                }

                // Only calculate agreement for the given feature
                FeatureStructure fs = cfg.getFs(user, aCasMap);

                values[i] = extractValueForAgreement(fs, aFeature, cfg.getAID(user).index,
                        cfg.getPosition().getLinkCompareBehavior());

                // Consider empty/null feature values to be the same and do not exclude them from
                // agreement calculation. The empty label is still a valid label.
                if (aNullLabelsAsEmpty && values[i] == null) {
                    values[i] = "";
                }

                // "null" cannot be used in agreement calculations. We treat these as incomplete
                if (values[i] == null) {
                    cfgSet.addTags(Tag.INCOMPLETE_LABEL);
                    if (aExcludeIncomplete) {
                        continue nextPosition;
                    }
                }

                i++;
            }

            if (ObjectUtils.notEqual(values[0], values[1])) {
                cfgSet.addTags(Tag.DIFFERENCE);
            }

            // If the position feature is set (subposition), then it must match the feature we
            // are calculating agreement over
            assert cfgSet.getPosition().getFeature() == null
                    || cfgSet.getPosition().getFeature().equals(aFeature);

            cfgSet.addTags(Tag.COMPLETE);
            study.addItemAsArray(values);
        }

        var taggedSets = aDiff.getPositions().stream() //
                .map(aDiff::getConfigurationSet) //
                .toList();

        return new FullCodingAgreementResult(aType, aFeature, aDiff.toResult(), study, users,
                taggedSets, aExcludeIncomplete);
    }

    private static Object extractValueForAgreement(FeatureStructure aFs, String aFeature,
            int aLinkIndex, LinkCompareBehavior aLCB)
    {
        var isPrimitiveFeature = aFs.getType().getFeatureByBaseName(aFeature).getRange()
                .isPrimitive();

        // If the feature on a position is set, then it is a subposition
        var isSubPosition = aLinkIndex != -1;

        // BEGIN PARANOIA
        assert aFs.getType().getFeatureByBaseName(aFeature).getRange()
                .isPrimitive() == isPrimitiveFeature;
        // primitive implies not subposition - if this is primitive and subposition, we
        // should never have gotten here in the first place.
        assert !isPrimitiveFeature || !isSubPosition;
        // END PARANOIA

        if (isPrimitiveFeature && !isSubPosition) {
            // Primitive feature / primary position
            return getFeature(aFs, aFeature);
        }
        else if (!isPrimitiveFeature && isSubPosition) {
            // Link feature / sub-position
            return extractLinkFeatureValueForAgreement(aFs, aFeature, aLinkIndex, aLCB);
        }
        else {
            throw new IllegalStateException("Should never get here: primitive: "
                    + aFs.getType().getFeatureByBaseName(aFeature).getRange().isPrimitive()
                    + "; subpos: " + isSubPosition);
        }
    }

    private static Object extractLinkFeatureValueForAgreement(FeatureStructure aFs, String aFeature,
            int aLinkIndex, LinkCompareBehavior aLCB)
    {
        @SuppressWarnings("unchecked")
        var links = (ArrayFS<FeatureStructure>) aFs
                .getFeatureValue(aFs.getType().getFeatureByBaseName(aFeature));
        var link = links.get(aLinkIndex);

        switch (aLCB) {
        case LINK_TARGET_AS_LABEL:
            // FIXME The target feature name should be obtained from the feature definition!
            var target = (AnnotationFS) link
                    .getFeatureValue(link.getType().getFeatureByBaseName("target"));

            return target.getBegin() + "-" + target.getEnd() + " [" + target.getCoveredText() + "]";
        case LINK_ROLE_AS_LABEL:
            // FIXME The role feature name should be obtained from the feature definition!
            return link.getStringValue(link.getType().getFeatureByBaseName("role"));
        default:
            throw new IllegalStateException("Unknown link target comparison mode [" + aLCB + "]");
        }
    }

    public static void dumpAgreementStudy(PrintStream aOut, FullCodingAgreementResult aAgreement)
    {
        try {
            aOut.printf("Category count: %d%n", aAgreement.getCategoryCount());
        }
        catch (Throwable e) {
            aOut.printf("Category count: %s%n", ExceptionUtils.getRootCauseMessage(e));
        }
        try {
            aOut.printf("Item count: %d%n", aAgreement.getStudy().getItemCount());
        }
        catch (Throwable e) {
            aOut.printf("Item count: %s%n", ExceptionUtils.getRootCauseMessage(e));
        }

        aOut.printf("Relevant position count: %d%n", aAgreement.getRelevantSetCount());

        aOut.printf("%n== Complete sets: %d ==%n", aAgreement.getCompleteSets().size());
        dumpAgreementConfigurationSetsWithItems(aOut, aAgreement, aAgreement.getCompleteSets());

        aOut.printf("%n== Incomplete sets (by position): %d == %n",
                aAgreement.getIncompleteSetsByPosition().size());
        dumpAgreementConfigurationSets(aOut, aAgreement, aAgreement.getIncompleteSetsByPosition());

        aOut.printf("%n== Incomplete sets (by label): %d ==%n",
                aAgreement.getIncompleteSetsByLabel().size());
        dumpAgreementConfigurationSets(aOut, aAgreement, aAgreement.getIncompleteSetsByLabel());

        aOut.printf("%n== Plurality sets: %d ==%n", aAgreement.getPluralitySets().size());
        dumpAgreementConfigurationSets(aOut, aAgreement, aAgreement.getPluralitySets());
    }

    private static void dumpAgreementConfigurationSetsWithItems(PrintStream aOut,
            FullAgreementResult_ImplBase<ICodingAnnotationStudy> aAgreement,
            List<ConfigurationSet> aSets)
    {
        int i = 0;
        for (var item : aAgreement.getStudy().getItems()) {
            var sb = new StringBuilder();
            sb.append(aSets.get(i).getPosition());
            for (var unit : item.getUnits()) {
                if (sb.length() > 0) {
                    sb.append(" \t");
                }
                sb.append(unit.getCategory());
            }
            aOut.println(sb);
            i++;
        }
    }

    private static void dumpAgreementConfigurationSets(PrintStream aOut,
            FullAgreementResult_ImplBase<ICodingAnnotationStudy> aAgreement,
            List<ConfigurationSet> aSets)
    {
        for (var cfgSet : aSets) {
            StringBuilder sb = new StringBuilder();
            sb.append(cfgSet.getPosition());
            for (Configuration cfg : cfgSet.getConfigurations()) {
                if (sb.length() > 0) {
                    sb.append(" \t");
                }
                sb.append(cfg.toString());
            }
            aOut.println(sb);
        }
    }
}
