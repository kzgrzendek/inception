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
package de.tudarmstadt.ukp.inception.curation.sidebar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tudarmstadt.ukp.inception.preferences.PreferenceKey;
import de.tudarmstadt.ukp.inception.preferences.PreferenceValue;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CurationSidebarManagerPrefs
    implements PreferenceValue
{
    private static final long serialVersionUID = -7731274322176414665L;

    public static final PreferenceKey<CurationSidebarManagerPrefs> KEY_CURATION_SIDEBAR_MANAGER_PREFS = //
            new PreferenceKey<>(CurationSidebarManagerPrefs.class,
                    "annotation/editor/curation-sidebar/manager");

    private boolean autoMergeCurationSidebar = true;

    public boolean isAutoMergeCurationSidebar()
    {
        return autoMergeCurationSidebar;
    }

    public void setAutoMergeCurationSidebar(boolean aAutoMergeCurationSidebar)
    {
        autoMergeCurationSidebar = aAutoMergeCurationSidebar;
    }

}
