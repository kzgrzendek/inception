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
package de.tudarmstadt.ukp.inception.io.bioc.model;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "collection")
public class BioCCollection
    extends BioCObject
{
    private String source;
    private String date;
    private String key;
    private List<BioCDocument> documents;

    public String getSource()
    {
        return source;
    }

    @XmlElement(name = "source")
    public void setSource(String aSource)
    {
        source = aSource;
    }

    public String getDate()
    {
        return date;
    }

    @XmlElement(name = "date")
    public void setDate(String aDate)
    {
        date = aDate;
    }

    public String getKey()
    {
        return key;
    }

    @XmlElement(name = "key")
    public void setKey(String aKey)
    {
        key = aKey;
    }

    public List<BioCDocument> getDocuments()
    {
        return documents;
    }

    @XmlElement(name = "document")
    public void setDocuments(List<BioCDocument> aDocuments)
    {
        documents = aDocuments;
    }
}
