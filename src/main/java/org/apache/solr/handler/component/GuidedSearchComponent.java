package org.apache.solr.handler.component;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.ConfigSolr;
import org.apache.solr.core.SolrResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;



/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class GuidedSearchComponent extends SearchComponent {


    protected NamedList initParams;
    protected String guidedConfigFileName;
    protected Document guidedConfigXml;

    @Override
    public void init(NamedList args) {
        this.initParams = args;

        guidedConfigFileName = args.get("config_file").toString();

        log.info("guidedConfigFileName:" + guidedConfigFileName);

        SolrResourceLoader loader = new SolrResourceLoader(null, null);
        ConfigSolr config = ConfigSolr.fromSolrHome(loader, loader.getInstanceDir());
        log.info("guided file path3: " + loader.getInstanceDir()+config.getDefaultCoreName()+ "/conf/" + guidedConfigFileName);

        File f = new File(loader.getInstanceDir()+config.getDefaultCoreName()+ "/conf/" + guidedConfigFileName);
        //if (f.exists()) log.info("The file exists4");

        if(f.canRead())  {
            try {
                //log.info("File Test2:" + f.);
                log.info("Opening " + guidedConfigFileName, GuidedSearchComponent.class);
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(f);
                guidedConfigXml = doc;
                //log.info("XML Test3:" + doc.getDocumentElement().getNodeName());

            } catch (ParserConfigurationException e) {
                log.error("Error creating instance of xml DocumentBuilder: " + e.toString(), GuidedSearchComponent.class);
            } catch (IOException e) {
                log.error("IO error parsing guided xml: " + e.toString());
            } catch (SAXException e) {
                log.error("SAX error parsing guided xml: " + e.toString());
            }
        }

    }


    protected String breadCrumb;
    protected String breadCrumbIDs;
    protected NamedList facetKeys = new NamedList();
    protected NamedList parentCat = new NamedList();
    protected NamedList childCats = new NamedList();


    @SuppressWarnings("unchecked")
    protected void addGuidedRequestParams(ModifiableSolrParams n_params) {

        breadCrumb = "";
        breadCrumbIDs = "";
        this.parentCat.clear();
        this.childCats.clear();

        Document d = guidedConfigXml;
        String guided_id = null;
        if (n_params.get("guided_id") != null) {
            guided_id = n_params.get("guided_id");
            //log.info(guided_id);
        } else {
            log.warn("guided_id param was not provided. This is required for guided searches.");
            return;
        }

        //turn faceting on
        n_params.add("facet", "true");
        facetKeys.clear(); //= new NamedList();
        NodeList nl = d.getElementsByTagName("category");
        for (int i=0;i < nl.getLength(); i++) {
            Node cdoc = nl.item(i);
            if (cdoc.getAttributes().getNamedItem("id").getNodeValue().equals(guided_id.toString())) {

//	    		//set the parent category
//	    		try {
//	    			Node prnt = cdoc.getParentNode();
//	    			this.parentCat.add(prnt.getAttributes().getNamedItem("id").getNodeValue(),prnt.getAttributes().getNamedItem("fieldName").getTextContent());
//	    		} catch (Exception e){
//	    			//no parent
//	    		}
//	    		}

                this.setGuidedMeta(cdoc); //set the breadcrumb based on the current category.

                NodeList cparams = cdoc.getChildNodes();
                for (int x = 0; x < cparams.getLength(); x++) {
                    Node cparam = cparams.item(x);
                    //what kind of parameter are we adding?
                    //log.info(cparam.getNodeName());
                    if (cparam.getNodeName().equals("facetField")) {
                        String field = cparam.getAttributes().getNamedItem("fieldName").getTextContent();
                        String key =  cparam.getAttributes().getNamedItem("displayName").getTextContent();
                        facetKeys.add(field, key);
                        //add the facet field to the solr request
                        if (cparam.getAttributes().getNamedItem("range") == null || cparam.getAttributes().getNamedItem("range").getTextContent() == "false") {
                            //n_params.add("facet.field", "{!key='" + cparam.getAttributes().getNamedItem("displayName").getTextContent() + "'}" +
                            //cparam.getAttributes().getNamedItem("fieldName").getTextContent());
                            n_params.add("facet.field", field);

                        } else if (cparam.getAttributes().getNamedItem("range").getTextContent().equals("true"))
                        {
                            NodeList rngs = cparam.getChildNodes();
                            for (int y=0;y<rngs.getLength();y++)
                            {
                                Node rng = rngs.item(y);
                                if (rng.getNodeName().equals("rangeQuery")) {
                                    key = rng.getAttributes().getNamedItem("displayName").getTextContent();
                                    String fquery = rng.getTextContent();
                                    facetKeys.add(fquery, key);
                                    n_params.add("facet.query", fquery);

                                }
                            }
                        }

                        //facetKeys.add("key", cparam.getAttributes().getNamedItem("displayName").getTextContent());

                        //add the guided info that will get added to response
                        //NamedList guidedFacet = new NamedList();
                        //guidedFacet.add("field", cparam.getAttributes().getNamedItem("fieldName").getTextContent());
                        //guidedFacet.add("displayName", cparam.getAttributes().getNamedItem("displayName").getTextContent());

                        //facetInfo.add("facet", guidedFacet);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void setGuidedMeta(Node n) {

        //find the breadcrumb
        breadCrumb = n.getAttributes().getNamedItem("name").getNodeValue();
        breadCrumbIDs = n.getAttributes().getNamedItem("id").getNodeValue();
        //log.info(catName);
        String p;
        String pID;

        //get the parent
        if (n.getParentNode() != null) {
            Node pn = n.getParentNode();
            if (pn.getNodeName().equals("category"))
                this.parentCat.add(pn.getAttributes().getNamedItem("id").getNodeValue(),
                        pn.getAttributes().getNamedItem("name").getNodeValue());
        }

        //get the children
        if (n.hasChildNodes()) {
            NodeList childn = n.getChildNodes();
            for (int i=0;i<childn.getLength();i++) {
                Node child = childn.item(i);
                if (child.getNodeName().equals("category")) {
                    this.childCats.add(child.getAttributes().getNamedItem("id").getNodeValue(),
                            child.getAttributes().getNamedItem("name").getNodeValue());
                }

            }
        }

        //build breadcrumb strings
        while (n.getParentNode() != null) {
            n = n.getParentNode();
            try {
                if (n.getAttributes().getNamedItem("name") != null) {
                    p = n.getAttributes().getNamedItem("name").getNodeValue();
                    pID = n.getAttributes().getNamedItem("id").getNodeValue();
                    breadCrumb = p + " > " + breadCrumb;
                    breadCrumbIDs = pID + "," + breadCrumbIDs;
                }
            } catch (NullPointerException e) {
                //do nothing
            }
        }




        //end breadcrumb

    }

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {

        //get the current request params
        //NamedList n_params = rb.req.getParams().toNamedList();

        ModifiableSolrParams n_params = new ModifiableSolrParams(rb.req.getParams());

        //update them
        this.addGuidedRequestParams(n_params);

        //set them to the request
        //rb.req.setParams(SolrParams.toSolrParams(n_params));
        rb.req.setParams(n_params);

    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {

        rb.rsp.add("guided", this.getGuidedResponseParams(rb.req.getParams().toNamedList()));

    }

    public NamedList getGuidedResponseParams(NamedList params) {

        NamedList guided = new NamedList();


        //String breadcrumb = "Systems > Notebooks";
        //guided.add("breadcrumb",breadcrumb);
        Document d = guidedConfigXml;
        String guided_id = null;
        if (params.get("guided_id") != null) {
            guided_id = params.get("guided_id").toString();
            //log.info(guided_id);
        } else {
            //log.warn("guided_id param was not provided. This is required for guided searches.");
            return null;
        }


        guided.add("breadcrumb", this.breadCrumb);
        guided.add("breadcrumb_ids", this.breadCrumbIDs);
        guided.add("parent", this.parentCat);
        guided.add("children", this.childCats);
        guided.add("facet_labels", this.facetKeys);
        //get the breadcrumb



        return guided;
    }


    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getSource() {
        return null;
    }

    private static Logger log = LoggerFactory.getLogger(GuidedSearchComponent.class);


}
