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
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.actionbar.export;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalDialog;
import org.apache.wicket.model.IModel;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.support.bootstrap.BootstrapModalDialog;

/**
 * Dialog providing allowing the annotator to download the current document.
 */
public class ExportDocumentDialog
    extends BootstrapModalDialog
{
    private static final long serialVersionUID = 671214149298791793L;

    private final ExportDocumentDialogContent content;

    public ExportDocumentDialog(String id, final IModel<AnnotatorState> aModel)
    {
        super(id);

        closeOnEscape();
        closeOnClick();

        setContent(content = new ExportDocumentDialogContent(ModalDialog.CONTENT_ID, aModel));
    }

    public void show(AjaxRequestTarget aTarget)
    {
        content.onShow(aTarget);
        open(aTarget);
    }
}
