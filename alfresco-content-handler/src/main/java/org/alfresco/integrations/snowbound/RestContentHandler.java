 package com.snowbound.alfresco.integrations;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonObjectParser;

import com.google.api.client.json.jackson.JacksonFactory;
import com.google.gson.Gson;

import com.snowbound.alfresco.integrations.entity.Annotation;
import com.snowbound.alfresco.integrations.entity.AnnotationList;
import com.snowbound.alfresco.integrations.entity.Note;
import com.snowbound.alfresco.integrations.entity.ReturnClientInstanceId;

 
import com.snowbound.alfresco.integrations.entity.Bookmark;
import com.snowbound.alfresco.integrations.entity.Document;
import com.snowbound.alfresco.integrations.entity.PreferenceXML;
import com.snowbound.common.utils.ClientServerIO;
import com.snowbound.common.utils.Logger;
import com.snowbound.snapserv.servlet.*;


 
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;


import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;


import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Author: Kyle Adams
 * Date: 2/11/13
 * Time: 5:11 PM
 */
public class RestContentHandler implements FlexSnapSIContentHandlerInterface, FlexSnapSISaverInterface,DocumentNotesInterface {
	Logger logger = Logger.getInstance();

    public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
   
    
    private HttpRequestFactory requestFactory;

    private String nodeRef;
    private String authenticationTicket;
    
    private String currentAlfrescoUser;
    private Properties properties = null;
    private Document document = null;
    private List<Annotation> annotationList = null;
    private Bookmark bookmark = null;
    private Note note = null;
    private String newNodeRef = "";
    private PreferenceXML preferenceXML = null;
    private Map<String,Annotation> annotationHashMap = null;
    
    private Hashtable existingDeleteHash = null;
    
    
    
    
    
    private String returnDocumentId = "";
    

    protected static final String PARAM_FILE_PATH = "filePath";
    protected static String gFilePath = "c:/imgs/";
  
    protected static final String PARAM_SUPPORT_REDCATIONS = "supportRedactions";
    protected static String gSupportRedactions = "false";
    
    private String alfrescoBaseUrl = null;
  
    private Set<String> groupSet =  new HashSet<String>() ;	 
   
    
    private static final String LINE_FEED = "\r\n";
    


    public RestContentHandler(){
        this.setPropertiesFile();
    }

    private void setPropertiesFile(){
       	
        properties = new Properties();

        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("snowbound.properties");
        try {
       	 	properties.load(inputStream);
            } catch (IOException e) {
           	  logger.printStackMessage( e);
        	 }finally{
        		try {
					inputStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	 }
        }
     
    

    public void init(ServletConfig servletConfig) throws FlexSnapSIAPIException {
   //	logger.log( "Using servlet: " + servletConfig.getServletName());
         
        alfrescoBaseUrl = properties.getProperty("alfresco.base.url");
       

	     String pathParam = servletConfig.getInitParameter(PARAM_FILE_PATH);
	     	if (pathParam != null)
	         {
	             setFilePath(pathParam, servletConfig.getServletContext());
      		 }
	     	
	     	gSupportRedactions =  servletConfig.getInitParameter(PARAM_SUPPORT_REDCATIONS).toLowerCase();
	         if (gSupportRedactions == null)
	         {
	         	gSupportRedactions = "false";
	        }

        }


        public static void setFilePath(String pathParam, ServletContext context)
        {
            if ((pathParam.startsWith("./") || pathParam.startsWith(".\\"))
                && context != null)
            {
                gFilePath = context.getRealPath(pathParam.substring(2))
                    + File.separator;
            }
            else
            {
                gFilePath = pathParam;
            }
            
             
        }



    public ContentHandlerResult getAvailableDocumentIds(ContentHandlerInput contentHandlerInput) {
  
    	//logger.log(  "Entering getAvailableDocumentIds method...");
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();

        String[] availableDocuments = new String[1];
        availableDocuments[0]=contentHandlerInput.getDocumentId();
        try{
            contentHandlerResult.put("KEY_AVAILABLE_DOCUMENT_IDS", availableDocuments);
        }
        catch (Exception e){
        	logger.printStackMessage( e);
        }
        return contentHandlerResult;
    }

    public ContentHandlerResult getDocumentContent(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
    	
     

    //	logger.log( "Entering getDocumentContent method...");
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();
        try{
            nodeRef = contentHandlerInput.getDocumentId();
            
           // logger.log( "test Noderef: " +  nodeRef);
            
            if(nodeRef.equalsIgnoreCase("notallowed")){
            	logger.log(  "Failed to save document content: do not have permission " );
            	 
            	
            	 contentHandlerResult.put(contentHandlerResult.ERROR_MESSAGE,"User does not have permission");
            	 return contentHandlerResult;
              
         	 
             }	
            	
      
            
           
            nodeRef =  URLDecoder.decode(nodeRef);
          
      
         
             
          
          
            String  ClientInstanceIDString   = contentHandlerInput.getClientInstanceId();
            ClientInstanceIDString = URLDecoder.decode(ClientInstanceIDString);
            ClientInstanceIDString = ClientInstanceIDString.replaceAll("\\r\\n", "");
            Gson gson = new Gson();
            ReturnClientInstanceId  returnClientIdObj = gson.fromJson ( ClientInstanceIDString  , ReturnClientInstanceId.class);
        
            authenticationTicket = returnClientIdObj.getTicket();
            currentAlfrescoUser = returnClientIdObj.getUserName();
            
            
               
                  // authenticationTicket = URLDecoder.decode(authTicket);
                
                   
            
            // split ClientInstanceId for Alfresco ticket and current user
            
         /*   int pt = authTicket.indexOf('*');
           
            if (pt >= 0) {
            	 authenticationTicket = authTicket.substring(0, pt);
            	 currentAlfrescoUser =  authTicket.substring(pt + 1);
            	 // kluge for new document
            	 currentAlfrescoUser =  currentAlfrescoUser.replace("-newpdf", "").replace("-newtiff", "").replace("-new", "");
            	  
            } else {
            	 authenticationTicket = authTicket;
            	 currentAlfrescoUser  = "";
            } */
           
           
            
        // retrieve document bytes ---------------------------------   
            GenericUrl documentUrl = new GenericUrl(
                    this.alfrescoBaseUrl +
                            "/service/integrations/snowbound/GetDocument?alf_ticket=" +
                            authenticationTicket +
                            "&nodeRef=workspace://SpacesStore/" + nodeRef  );
                           
           
        
       //  System.out.print("documentUrl:  " + documentUrl); 
          byte[] docBytes = null;
          //-----------------------------
          URL url = null;
          InputStream urlStream = null;
          String getDocName = "";
          String getDocId = "";
          
          String sURL =  documentUrl.toString();
           URLConnection conn = null;
            document = new Document();
          try
          {
              sURL = sURL.trim().replaceAll(" ", "%20");
              url = new URL(sURL);
              conn =   url.openConnection();
              getDocName = conn.getHeaderField("docName");
              document.setName(getDocName);
              getDocId = conn.getHeaderField("docId");
              document.setId(getDocId);
             urlStream = conn.getInputStream();
            
             
             docBytes = ClientServerIO.getBytes(urlStream);
             document.setContent(docBytes);
            
             
          }
          catch (FileNotFoundException fnfe)
          {
             
              logger.log(  "File Not Found at URL: " + sURL);
               
                  return null;
               
          }
          catch (Exception e)
          {
              Logger.getInstance().printStackTrace(e);
         
              
              return null;
              
          }finally{
        	  closeQuietly( urlStream);
               
        	 
          }
      
          
         
         //   Kyles's code changed Gson issue w/ larger AFP files
         //  document.setContent(getDocBytes);
         //   String documentJsonResponse = sendHttpRequest(documentUrl).parseAsString();
         //   document = new Gson().fromJson(documentJsonResponse, Document.class);
          
         
          
                    
         
        
            contentHandlerResult.put(ContentHandlerResult.KEY_DOCUMENT_CONTENT, docBytes);
       
            contentHandlerResult.put(ContentHandlerResult.KEY_DOCUMENT_DISPLAY_NAME,  URLDecoder.decode(getDocName,"UTF-8"));
            
            
            
      
         
          
           
//---test alternative permission check snippet--------------------------------------------------
	     /*	
	 
      	  GenericUrl  groupUrl = new GenericUrl(
      			  this.alfrescoBaseUrl +
                    "/service/api/people/" + currentAlfrescoUser + "?alf_ticket=" +
                    authenticationTicket + "&groups=true" +
                    "&nodeRef=" + nodeRef.replace("workspace/", "workspace://") +
                    "&format=json");
      
      String groupJsonResponse = sendHttpRequest(groupUrl).parseAsString();
    
      org.json.simple.parser.JSONParser p = new org.json.simple.parser.JSONParser();
   
      Object o = p.parse(groupJsonResponse);
     
      if (o instanceof org.json.simple.JSONObject)
      {
          org.json.simple.JSONObject jsonRes = (org.json.simple.JSONObject) o;
          org.json.simple.JSONArray groupsList;
          groupsList = (org.json.simple.JSONArray) jsonRes.get("groups");
          Iterator i = groupsList.iterator();
          groupSet.clear();
          while (i.hasNext())
          {                            
              org.json.simple.JSONObject group = (org.json.simple.JSONObject) i.next();
              String currGroupName = group.get("itemName").toString();
              
              groupSet.add(currGroupName);
              
          }
      }
      
      //System.out.println("returned groups:"  + groupSet.toString());  
   
     */ 
      

//---------------------------------------------------------------------------------------

            
            
             
             
        }
        catch (Exception e){
        	logger.log("get document content error " +  e.toString() );
        	 
            return null;
        }
        
        return contentHandlerResult;
    }

    public ContentHandlerResult eventNotification(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public ContentHandlerResult getAnnotationNames(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();

        try {
           // logger.log(  "Entering getAnnotationNames method...");
            nodeRef  = contentHandlerInput.getDocumentId();
           
         
       //    System.out.print("Annotation names node: " + nodeRef   );
             
           String authTicket = contentHandlerInput.getClientInstanceId();
                  authTicket = URLDecoder.decode(authTicket);
                  authTicket = authTicket.replaceAll("\\r\\n", "");
              //     System.out.print("Annotation names authTicket: " + authTicket   );
                  Gson gson = new Gson();
                  ReturnClientInstanceId  returnClientIdObj = gson.fromJson ( authTicket  , ReturnClientInstanceId.class);
                 
                  authTicket = returnClientIdObj.getTicket();
                  currentAlfrescoUser = returnClientIdObj.getUserName();
                  
                
                  
                  
            // split ClientInstanceId for Alfresco ticket and current user
            
         /*   int pt = authTicket.indexOf('*');
            if (pt >= 0) {
            	 authenticationTicket = authTicket.substring(0, pt);
            	 currentAlfrescoUser =  authTicket.substring(pt + 1);
            	 // kluge for new document
            	 currentAlfrescoUser =  currentAlfrescoUser.replace("-newpdf", "").replace("-newtiff", "").replace("-new", "");
            } else {
            	 authenticationTicket = authTicket;
            	 currentAlfrescoUser  = "";
            } */


            GenericUrl annotationsUrl = new GenericUrl(
            this.alfrescoBaseUrl +
                    "/service/integrations/snowbound/GetAnnotations?alf_ticket=" +
                    authenticationTicket +
                    "&nodeRef=workspace://SpacesStore/" + nodeRef +
                    "&format=json");
           // logger.log(  "Annotation Request Url: " + annotationsUrl);


            String annotationsJsonResponse = sendHttpRequest(annotationsUrl).parseAsString();
          
            AnnotationList annotations = new Gson().fromJson(annotationsJsonResponse, AnnotationList.class);
            //logger.log(  "Annotation From Json: " + annotations);


            annotationHashMap = new HashMap<String, Annotation>();
         
            annotationList = annotations.getAnnotations();
            
           
          
            

            String[] annotationNames = new String[annotations.getAnnotations().size()];
            
           
             
            
            
            if (annotationList != null) {
                for (int i=0; i< annotationList.size(); i++){
                    Annotation annotation = annotationList.get(i);
                    String name = annotation.getName();
                    annotationHashMap.put(name, annotation);
                    annotationNames[i] = name; 
                 
           
              
            }
            
  
            }     
            
            
         
            
            
            
            
            
            
            if ( annotationNames != null  ) { 
            	
            	 
            	 
            	contentHandlerResult.put(ContentHandlerResult.KEY_ANNOTATION_NAMES, annotationNames);
            } else {
            	 
            	contentHandlerResult.put(ContentHandlerResult.KEY_ANNOTATION_NAMES, ContentHandlerResult.VOID );	
            }
          
        }
        catch (Exception e) {
           
            logger.log(  "Failed to get annotation names: " + e.toString());
        }
        
        return contentHandlerResult;
    }
   
    
    
    public ContentHandlerResult getAnnotationNamesOld (ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();

        try {
          //  logger.log(  "Entering getAnnotationNames method...");
            nodeRef  = contentHandlerInput.getDocumentId();
          
         
       
             
           //String authTicket = contentHandlerInput.getClientInstanceId();
           //       authTicket = URLDecoder.decode(authTicket);
        
                  String  ClientInstanceIDString   = contentHandlerInput.getClientInstanceId();
                  ClientInstanceIDString = URLDecoder.decode(ClientInstanceIDString);
                  ClientInstanceIDString = ClientInstanceIDString.replaceAll("\\r\\n", "");
                  Gson gson = new Gson();
                  ReturnClientInstanceId  returnClientIdObj = gson.fromJson ( ClientInstanceIDString  , ReturnClientInstanceId.class);
              
                  authenticationTicket = returnClientIdObj.getTicket();
                  currentAlfrescoUser = returnClientIdObj.getUserName();
                    
            
            
       /*     int pt = authTicket.indexOf('*');
            if (pt >= 0) {
            	 authenticationTicket = authTicket.substring(0, pt);
            	 currentAlfrescoUser =  authTicket.substring(pt + 1);
            	 // flag for new document
            	 currentAlfrescoUser =  currentAlfrescoUser.replace("-new", "");
            } else {
            	 authenticationTicket = authTicket;
            	 currentAlfrescoUser  = "";
            } */


            GenericUrl annotationsUrl = new GenericUrl(
            this.alfrescoBaseUrl +
                    "/service/integrations/snowbound/GetAnnotations?alf_ticket=" +
                    authenticationTicket +
                    "&nodeRef=workspace://SpacesStore/" + nodeRef +
                    "&format=json");
            logger.log(  "Annotation Request Url: " + annotationsUrl);


            String annotationsJsonResponse = sendHttpRequest(annotationsUrl).parseAsString();
            AnnotationList annotations = new Gson().fromJson(annotationsJsonResponse, AnnotationList.class);
       

            annotationHashMap = new HashMap<String, Annotation>();
            annotationList = annotations.getAnnotations();

            String[] annotationNames = new String[annotations.getAnnotations().size()];
          


            if (annotationList != null) {
                for (int i=0; i< annotationList.size(); i++){
                    Annotation annotation = annotationList.get(i);
                    String name = annotation.getName();
                    annotationHashMap.put(name, annotation);

                
                    annotationNames[i] = name;
                }
                
              
            }
            
            
          
             
            
            	contentHandlerResult.put(ContentHandlerResult.KEY_ANNOTATION_NAMES, annotationNames);
           
           
            
          
        }
        catch (Exception e) {
           
            logger.log(  "Failed to get annotation names: " + e.toString());
        }
        
        return contentHandlerResult;
    }
   /* 
    public ContentHandlerResult getNotes(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();

        try {
           // logger.log(  "Entering getAnnotationNames method...");
            nodeRef  = contentHandlerInput.getDocumentId();
          
         
       //    System.out.print("Annotation names node: " + nodeRef   );
             
           String authTicket = contentHandlerInput.getClientInstanceId();
                  authTicket = URLDecoder.decode(authTicket);
          
            // split ClientInstanceId for Alfresco ticket and current user
            
            int pt = authTicket.indexOf('*');
            if (pt >= 0) {
            	 authenticationTicket = authTicket.substring(0, pt);
            	  
            } else {
            	 authenticationTicket = authTicket;
            	 
            }


            GenericUrl notesUrl = new GenericUrl(
            this.alfrescoBaseUrl +
                    "/service/integrations/snowbound/GetNotes?alf_ticket=" +
                    authenticationTicket +
                    "&nodeRef=workspace://SpacesStore/" + nodeRef +
                    "&format=json");
           // logger.log(  "Annotation Request Url: " + annotationsUrl);


            String notesJsonResponse = sendHttpRequest(notesUrl).parseAsString();
          
            NoteList notes = new Gson().fromJson(notesJsonResponse, NoteList.class);
            //logger.log(  "Annotation From Json: " + annotations);


            noteHashMap = new HashMap<String, Note>();
         
            noteList = notes.getNotes();
            
           
          
            

            String[] noteStrings = new String[notes.getNotes().size()];
            
           
             
            
            
            if (noteList != null) {
                for (int i=0; i< noteList.size(); i++){
                    Note note = noteList.get(i);
                    String name = note.getName();
                    noteHashMap.put(name, note);
                    noteStrings[i] = name; 
                 
           
              
            }
            
  
            }     
            
            
         
            
            
            
            
            
            
            if ( noteStrings != null  ) { 
            	
            	 
            	 
            	contentHandlerResult.put(ContentHandlerResult.KEY_NOTES_CONTENT, noteStrings);
            } else {
            	 
            	contentHandlerResult.put(ContentHandlerResult.KEY_ANNOTATION_NAMES, ContentHandlerResult.VOID );	
            }
          
        }
        catch (Exception e) {
           
            logger.log(  "Failed to get annotation names: " + e.toString());
        }
        
        return contentHandlerResult;
    }
   
  */ 
    
    public ContentHandlerResult getAnnotationProperties(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();
        
      
         nodeRef = contentHandlerInput.getDocumentId();
          nodeRef =  URLDecoder.decode(nodeRef);
        
        
        
        

        try{
        //	logger.log(  "Entering getAnnotationProperties method...");
            Hashtable properties = new Hashtable();

            Annotation annotation = annotationHashMap.get(contentHandlerInput.getAnnotationId());
            
         
            
           
            
         
          
            int checkPermissionLevel = 0;
             // permission based alfresco 
             checkPermissionLevel =  checkAlfrescoPermission(annotation);
         
            
          //  System.out.println("annotation id: " + annotation.getId() + " | " + annotation.getName() + " permission is : " + annotation.getPermissionLevel() + "  |  " + checkPermissionLevel );
           
          //alternative permission check snippet ------------------------------------------------
          //    checkPermissionLevel  = checkAlfrescoPermissionGroups(annotation.getOwner(), currentAlfrescoUser);
          //   System.out.println("The group permission is : " + checkPermissionLevel );
          //--------------------------------------------------------------------------------------     
            
                
      
            
           
            
          
            
            // annotation.getPermissionLevel() <=   PERM_REDACTION.intValue() 
             
             if( gSupportRedactions.equals("true") && annotation.getRedactionFlag()){
        	
        	    checkPermissionLevel = 10;
        	         
           }
            
             
            
             properties.put(AnnotationLayer.PROPERTIES_KEY_PERMISSION_LEVEL, checkPermissionLevel);
            
                   
            
            
            properties.put(AnnotationLayer.PROPERTIES_KEY_REDACTION_FLAG, new Boolean(annotation.getRedactionFlag()));
             

            contentHandlerResult.put(ContentHandlerResult.KEY_ANNOTATION_PROPERTIES, properties);
        }
        catch(Exception e){
             logger.log(  "Failed to get annotation properties: " + e.getMessage());
        }
          
        return contentHandlerResult;
    }

    public ContentHandlerResult getAnnotationContent(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();
        try{
        	// logger.log( "Entering getAnnotationContent method...");
        	 
        	
              

            if(!isNullOrEmpty(annotationHashMap)){
                Annotation annotation = annotationHashMap.get(contentHandlerInput.getAnnotationId());
                
                Hashtable props = null;
                ContentHandlerResult propsResult = getAnnotationProperties(contentHandlerInput);
                if (propsResult != null)
                {
                    props = propsResult.getAnnotationProperties();
               }
                
                contentHandlerResult.put(ContentHandlerResult.KEY_ANNOTATION_CONTENT, annotation.getContent());
                contentHandlerResult.put(ContentHandlerResult.KEY_ANNOTATION_DISPLAY_NAME, annotation.getName());
              
               
                

              
                if (props != null)
                {
                	 contentHandlerResult.put(ContentHandlerResult.KEY_ANNOTATION_PROPERTIES,
                               props);
                }
            }
            else{
            	return null;
            }


        }
        catch (Exception e){
        	 logger.log( "Failed to get annotation content: " + e.getMessage());
        }
        
       
        
        return contentHandlerResult;
    }

    public ContentHandlerResult getBookmarkContent(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();

        try{
        	//logger.log(  "Entering getBookmarkContent method...");

            GenericUrl bookmarkUrl = new GenericUrl(
                    this.alfrescoBaseUrl +
                            "/service/integrations/snowbound/GetBookmark?alf_ticket=" +
                            authenticationTicket +
                            "&nodeRef=workspace://SpacesStore/" + nodeRef  +
                            "&format=json");

            String documentJsonResponse = sendHttpRequest(bookmarkUrl).parseAsString();
            bookmark = new Gson().fromJson(documentJsonResponse, Bookmark.class);
            contentHandlerResult.put(ContentHandlerResult.KEY_BOOKMARK_CONTENT, bookmark.getContent());
        }
        catch(Exception e){
        	logger.log(  "Failed to get bookmark content: " + e.getMessage());
        }
          
        return contentHandlerResult;
    }
    
    
     public ContentHandlerResult getNotesContent(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();

        try{
        	//logger.log(  "Entering getNotesContent method...");
        	
        	// nodeRef = contentHandlerInput.getDocumentId();
             nodeRef =  URLDecoder.decode(nodeRef);
             
             String  ClientInstanceIDString   = contentHandlerInput.getClientInstanceId();
             ClientInstanceIDString = URLDecoder.decode(ClientInstanceIDString);
             ClientInstanceIDString = ClientInstanceIDString.replaceAll("\\r\\n", "");;
             Gson gson = new Gson();
             ReturnClientInstanceId  returnClientIdObj = gson.fromJson ( ClientInstanceIDString  , ReturnClientInstanceId.class);
         
             authenticationTicket = returnClientIdObj.getTicket();
             currentAlfrescoUser = returnClientIdObj.getUserName();
             
        //	 String authTicket = contentHandlerInput.getClientInstanceId();
        //     authTicket = URLDecoder.decode(authTicket);
     
        
       
     /*  int pt = authTicket.indexOf('*');
       if (pt >= 0) {
       	 authenticationTicket = authTicket.substring(0, pt);
       	  
       } else {
       	 authenticationTicket = authTicket;
       	 
       } */
        	                            

            GenericUrl noteUrl = new GenericUrl(
                    this.alfrescoBaseUrl +
                            "/service/integrations/snowbound/GetNote?alf_ticket=" +
                            authenticationTicket +
                            "&nodeRef=workspace://SpacesStore/" + nodeRef );
            
       //     System.out.println("get note url: " + noteUrl.toString());

            String noteJsonResponse = sendHttpRequest(noteUrl).parseAsString();
         
            note = new Gson().fromJson(noteJsonResponse, Note.class);
            
             
            
            contentHandlerResult.put(ContentHandlerResult.KEY_NOTES_CONTENT, note.getContent());
        }
        catch(Exception e){
        	logger.log(  "Failed to get Notes content: " + e.getMessage());
        }
          
        return contentHandlerResult;
    }  


    public ContentHandlerResult saveDocumentComponents(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();
        try{
        	
            String documentId = contentHandlerInput.getDocumentId();
          //  String clientInstanceId = contentHandlerInput.getDocumentId();
            String clientInstanceId = contentHandlerInput.getClientInstanceId();
            byte[] noteBytes =   contentHandlerInput.getNotesContent();
            HttpServletRequest request =   contentHandlerInput.getHttpServletRequest();
            returnDocumentId = documentId;
        	logger.log( "Begin saveDocumentComponents method..." + documentId + " clientInstanceId=" + clientInstanceId );
            
                  
        

            byte[] documentContent = contentHandlerInput.getDocumentContent();
            AnnotationLayer[] annotations = contentHandlerInput.getAnnotationLayers();
            byte[] bookmarkContent = contentHandlerInput.getBookmarkContent();

             boolean createDoc = false;
             
          // saving content ---------------------------------------------------------
             
            if (documentContent != null){
           	
            	String  ClientInstanceIDString   = contentHandlerInput.getClientInstanceId();
                ClientInstanceIDString = URLDecoder.decode(ClientInstanceIDString);
                ClientInstanceIDString = ClientInstanceIDString.replaceAll("\\r\\n", "");
                Gson gson = new Gson();
                ReturnClientInstanceId  returnClientIdObj = gson.fromJson ( ClientInstanceIDString  , ReturnClientInstanceId.class);
            
                 returnClientIdObj.getTicket();
            	 
            //	 if(clientInstanceId.contains("-new")) { 	
            	 if(returnClientIdObj.getNewDoc()) { 		 
            		
            		 createDoc = true;
            		
                       returnDocumentId = createDocumentContent(contentHandlerInput);
                       
            		  
                 	 //  returnDocumentId = testAlfrescoDocumentContent(contentHandlerInput); 
                 	                
            	  }else{
               saveDocumentContent(contentHandlerInput);
            	    
             
         
          	 
        
            	  }
           } else{
                  	logger.log( " Document not changed, not calling saveDocumentContent.");
                  }
            	
          // saving annotations---------------------------------------------------------  	
            	
            	
            int annIndex = 0;
           
            
            
        //      System.out.print("created document Id" + documentId);
         if (annotations != null){
        	 
         	 
        	 
        	  Hashtable existingDeleteHash = new Hashtable();  
        	  
              
              
        	  
                  for (annIndex = 0; annIndex < annotations.length; annIndex++){
                	  
                	  
                	 
                	 
                      contentHandlerInput.put(ContentHandlerInput.KEY_CLIENT_INSTANCE_ID, authenticationTicket);
                  	if(createDoc){
                  	
                  	  contentHandlerInput.put(ContentHandlerInput.KEY_DOCUMENT_ID, returnDocumentId);
                  	}else {
                      contentHandlerInput.put(ContentHandlerInput.KEY_DOCUMENT_ID, documentId);
                  	}
                  	
                  
                      contentHandlerInput.put(ContentHandlerInput.KEY_ANNOTATION_ID, annotations[annIndex].getLayerName() );
                    
                   
                    		  
                  //	logger.log( "component layer: " +   annotations[annIndex].getLayerName());
                	 
                      contentHandlerInput.put(ContentHandlerInput.KEY_ANNOTATION_CONTENT, annotations[annIndex].getData());
                      contentHandlerInput.put(ContentHandlerInput.KEY_ANNOTATION_PROPERTIES, annotations[annIndex].getProperties());
                      
                     
                      
                      	if(createDoc){
                             		saveCreatedAnnotationContent(contentHandlerInput, returnDocumentId);  
                      	}else{
                      	
                      	if(annotations[annIndex].isNew() || annotations[annIndex].isModified()){
                      		
                      	  saveAnnotationContent(contentHandlerInput  );
                      	}
                       
                      	}
                  
                
                  }
                  
               
                 
                   	 
          
                  }else{
            	logger.log(  " Annotation layer not changed: " );
                  }

          // saving document notes------------------------------------
        // save notes in xml files instead of database 
         
        // saveNotesContentFile(request, clientInstanceId, documentId, noteBytes);
       
         byte[] noteContent = contentHandlerInput.getNotesContent();
         if (noteContent != null){
        	 saveNotesContent(contentHandlerInput);
         }
           // saving bookmark content--------------------------------------
           // Bookmark not ready yet  VV version 4.3
           // if (bookmarkContent != null){
           // 	logger.log(  " Calling saveBookmarkContent method...");
             //   saveBookmarkContent(contentHandlerInput);
          //  }else{
          //  	logger.log(   " Bookmarks not changed.");
          //  }
         
        
          
        }
        catch (Exception e){
        	logger.log(   "Failed to save document components: " + e.getMessage());
        	  
            
        }
        logger.log( "removing duplicate layers");
         this.removeDuplicateLayers(contentHandlerInput);
        
        logger.log( "End saveDocumentComponents method..." + returnDocumentId);
        
        contentHandlerResult.put(ContentHandlerResult.DOCUMENT_ID_TO_RELOAD, returnDocumentId);
        
         
         
        
        return contentHandlerResult;
    }

    public ContentHandlerResult saveDocumentComponentsAs(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
         //Logger.getInstance().log(Logger.FINEST, "Begin saveDocumentComponentsAs method...");
        String documentKey = contentHandlerInput.getDocumentId();
        if (documentKey.equals("<EMPTY DOCUMENT>"))
        {
            documentKey = "NewDocument";
        }
         
        return saveDocumentComponents(contentHandlerInput);
    }
 
    
    public ContentHandlerResult saveDocumentContent(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();
        try{
        	 String documentKey =  URLDecoder.decode(contentHandlerInput.getDocumentId());
             byte[] documentContent = contentHandlerInput.getDocumentContent();
             
             
           
            
              

             if (documentContent == null){
                 return null;
             }
             
           
         	 //  System.out.print("Alfresco document");
             GenericUrl saveDocumentContentUrl = new GenericUrl(
                     this.alfrescoBaseUrl +
                             "/service/integrations/snowbound/SaveDocumentContent?alf_ticket=" +
                             authenticationTicket);
             Document document = new Document();
             document.setId("workspace://SpacesStore/" + documentKey);
             document.setContent(documentContent);
             document.setName(documentKey);
             String jsonString = new Gson().toJson(document);
             postJsonHttpRequest(saveDocumentContentUrl, jsonString.getBytes());
            
             contentHandlerResult.put(ContentHandlerResult.DOCUMENT_ID_TO_RELOAD, documentKey); 
             returnDocumentId  =  documentKey;
           
             
  
         }
         catch (Exception e){
         	logger.log(  "Failed to save document content: " + e.getMessage());
         	throw new FlexSnapSIAPIException("  Alfresco Document could not be saved " + e.getMessage());
         }
        
         return contentHandlerResult;
     }
    
   // public ContentHandlerResult createDocumentContent(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
    	
    	 public String createDocumentContent(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();
        try{
        	 String documentKey =  URLDecoder.decode(contentHandlerInput.getDocumentId());
        	// String clientInstanceId = contentHandlerInput.getClientInstanceId();
             byte[] documentContent = contentHandlerInput.getDocumentContent();
             
             
             
             
            
              

         
           //  System.out.print("non-Alfresco document");
             GenericUrl createDocumentContentUrl = new GenericUrl(
                     this.alfrescoBaseUrl +
                             "/service/integrations/snowbound/CreateDocument?alf_ticket=" +
                             authenticationTicket);
             Document document = new Document();
             document.setId("workspace://SpacesStore/" + nodeRef);
             document.setContent(documentContent);
             document.setName(documentKey);
             document.setMimeType("");
             // force pdf/tiff for saveas
             String  ClientInstanceIDString   = contentHandlerInput.getClientInstanceId();
             ClientInstanceIDString = URLDecoder.decode(ClientInstanceIDString);
             ClientInstanceIDString = ClientInstanceIDString.replaceAll("\\r\\n", "");
             Gson gson = new Gson();
             ReturnClientInstanceId  returnClientIdObj = gson.fromJson ( ClientInstanceIDString  , ReturnClientInstanceId.class);
         
             boolean isNew = false;
             isNew = returnClientIdObj.getNewDoc();
         	 
         //	 if(clientInstanceId.contains("-new")) { 	
         	 if( isNew) { 	
           //  if(clientInstanceId.contains("-newpdf")){
            	 
             if(returnClientIdObj.getMimeType().equalsIgnoreCase("pdf")){
           
             document.setMimeType("pdf");
             } 
         //    if(clientInstanceId.contains("-newtiff")){
             if(returnClientIdObj.getMimeType().equalsIgnoreCase("tiff")){
           
                 document.setMimeType("tiff");
             } 	 
            	 
         	 }
             
         	 
             String jsonString = new Gson().toJson(document);
             String  documentJsonResponse =  postJsonHttpRequestReturn(createDocumentContentUrl, jsonString.getBytes()).parseAsString();
          
             returnDocumentId  =   documentJsonResponse.replace("\"", "");
             
             
            
            
             contentHandlerResult.put(ContentHandlerResult.DOCUMENT_ID_TO_RELOAD,  returnDocumentId );
             
         	 

         } 
            catch (Exception e){
         	logger.log(  "Failed to save document content: " + e.getMessage());
         	throw new FlexSnapSIAPIException("New Alfresco Document could not be created " + e.getMessage());
         }
        
        // return contentHandlerResult;
         
        return returnDocumentId;
     }
    	 
    	 
    public ContentHandlerResult saveAnnotationContent(ContentHandlerInput contentHandlerInput  ) throws FlexSnapSIAPIException {
    	
        //Logger.getInstance().log(Logger.FINEST, " Begin saveAnnotationContent method...");
    
    	String documentId = contentHandlerInput.getDocumentId();
       String annotationLayer = contentHandlerInput.getAnnotationId();
       String clientInstanceId = contentHandlerInput.getClientInstanceId();
       byte[] annotationContent = contentHandlerInput.getAnnotationContent();
       Hashtable annProperties = contentHandlerInput.getAnnotationProperties();
       boolean     annotationDataIsEmpty = false;  

       try{
        
    	   String contentString = new String(annotationContent);
    	     
        	 
        	  
         
           
        	 //  logger.log("annotation layer ref:" + annotationLayer );
       
           if ((annotationContent != null) && ( annotationContent.length > 0) && (contentString.contains("pageNumber")) ){
               if (!annProperties.isEmpty()){
            	   
            	             	   
            	 String alfrescoId = "";
              	 if(annotationHashMap.containsKey(annotationLayer)){
                   alfrescoId =  annotationHashMap.get(annotationLayer).getId();
                 }
              // logger.log("Check layername: " +  annotationLayer + " | " +  alfrescoId );   
            	     
            	  
            	  
            	 
            	   Annotation annotation = new Annotation();
 
            	   
            	 if(documentId.equalsIgnoreCase(nodeRef) ){   
            	   this.deleteAnnotation( contentHandlerInput);
            	   annotation.setName(annotationLayer);
            	 }else{
            		 
            		 //copy to different document 
            		   
            		 annotation.setName(annotationLayer + "_" + "copy") ; 
            	 }
                                    
                 

                   Boolean tmpRedactionFlag = (Boolean) annProperties.get(AnnotationLayer.PROPERTIES_KEY_REDACTION_FLAG);
                  //   logger.log( " Annotation Redaction Flag: " + tmpRedactionFlag);

                   Integer tmpPermissionLevel = (Integer) annProperties.get(AnnotationLayer.PROPERTIES_KEY_PERMISSION_LEVEL);
                 //   logger.log(  " Annotation Permission Level: " + tmpPermissionLevel);
                   
                   
                   
                   boolean redactionFlag = false;
                   int permissionLevel = PERM_DELETE.intValue();
                     
                   if (tmpRedactionFlag != null){
                       redactionFlag = tmpRedactionFlag.booleanValue();
                       annotation.setRedactionFlag(redactionFlag);
                   }
                   
                
                   if(tmpPermissionLevel != null){
                   	permissionLevel = tmpPermissionLevel.intValue();
                   	  annotation.setPermissionLevel(permissionLevel);

                   }
                   
            

                    if (redactionFlag == true){
                   	annotation.setPermissionLevel(PERM_REDACTION.intValue());
                   }
                   else{
                       // annotation.setPermissionLevel(PERM_DELETE.intValue());
                        annotation.setPermissionLevel(permissionLevel);
                   }  
                 
                   annotation.setContent(annotationContent);
                  // annotation.setName(annotationLayer);
                   annotation.setParentNodeRef( "workspace://SpacesStore/" + documentId);
                   
                    annotation.setId("");
                    
                    

                  
                   
                   
                  
                  
                                  

                   String jsonString = new Gson().toJson(annotation);
                   GenericUrl saveAnnotationContentUrl = new GenericUrl(
                           this.alfrescoBaseUrl + "/service/integrations/snowbound/SaveAnnotationContent?alf_ticket=" + authenticationTicket);
                   postJsonHttpRequest(saveAnnotationContentUrl, jsonString.getBytes());
                   
              
                     
               }
           }
           else{
        	   // delete empty layers ajax is not getting rid of them
        	   this.deleteAnnotation( contentHandlerInput);
        	   
           }
       }
       catch (Exception e){
      	logger.printStackTrace(e);
       throw new FlexSnapSIAPIException("cannot save annotation " + e.toString());
       }
       
      this.removeDuplicateLayers(contentHandlerInput);
       return new ContentHandlerResult();
   }
    
    
    private void deleteUnsavedExistingLayers()
throws FlexSnapSIAPIException{
    	Enumeration hashEnum = this.existingDeleteHash.keys();
    	while (hashEnum.hasMoreElements())
    	{
    		
    		String deleteAnnotationId = (String) hashEnum.nextElement();
    		//  System.out.print("delete existing " + deleteAnnotationId);
    		this.deleteAnnotationLayer(deleteAnnotationId);
    	}
      
      this.existingDeleteHash.clear();
    
    }
    
  
   
    
    public ContentHandlerResult saveCreatedAnnotationContent(ContentHandlerInput contentHandlerInput, String returnId) throws FlexSnapSIAPIException {
        //Logger.getInstance().log(Logger.FINEST, " Begin saveAnnotationContent method...");

       String annotationLayer = contentHandlerInput.getAnnotationId();
       byte[] annotationContent = contentHandlerInput.getAnnotationContent();
       Hashtable annProperties = contentHandlerInput.getAnnotationProperties();
         

       try{
           if (annotationContent != null){
               if (!annProperties.isEmpty()){
                   Annotation annotation = new Annotation();

                   Boolean tmpRedactionFlag = (Boolean) annProperties.get(AnnotationLayer.PROPERTIES_KEY_REDACTION_FLAG);
                   

                   Integer tmpPermissionLevel = (Integer) annProperties.get(AnnotationLayer.PROPERTIES_KEY_PERMISSION_LEVEL);
                 
                   
                   
                   
                   boolean redactionFlag = false;
                   int permissionLevel = PERM_DELETE.intValue();
                     
                   if (tmpRedactionFlag != null){
                       redactionFlag = tmpRedactionFlag.booleanValue();
                       annotation.setRedactionFlag(redactionFlag);
                   }
                   
                   //-------------------
                   
                /*   if (tmpPermissionLevel != null)
                   {
                       permissionLevel = tmpPermissionLevel.intValue();
                   }
                   if (permissionLevel <= PERM_REDACTION.intValue())
                   {
                       fullFilePath = burnFilePath;
                   }
                   else if (redactionFlag == true)
                   {
                       fullFilePath = editFilePath;
                   } */
                   
                   //--------------------
                   
                   if(tmpPermissionLevel != null){
                   	permissionLevel = tmpPermissionLevel.intValue();
                   	  annotation.setPermissionLevel(permissionLevel);

                   }
                   annotation.setDocumentId(returnId);
                   annotation.setContent(annotationContent);
                   annotation.setName(annotationLayer);
                  
                   annotation.setParentNodeRef( "workspace://SpacesStore/" + returnId);
                   // create new annotation
                   annotation.setId("");
                   
                   if (permissionLevel <= PERM_REDACTION.intValue() )
                   {
                      // fullFilePath = burnFilePath;
                  	annotation.setPermissionLevel(PERM_REDACTION.intValue());
                	 
                   }
                   else if (redactionFlag == true)
                   {
                      // fullFilePath = editFilePath;
                       //annotation.setPermissionLevel(permissionLevel);
                   	annotation.setPermissionLevel(PERM_EDIT.intValue());
                   }
                    

                /*   if (redactionFlag == true){
                   	annotation.setPermissionLevel(PERM_REDACTION.intValue());
                   }
                   else{
                       // annotation.setPermissionLevel(PERM_DELETE.intValue());
                        annotation.setPermissionLevel(permissionLevel);
                   } */
                 
                   
                   
                   logger.log(  "creating annotation: " + annotationLayer + " redact: " + annotation.getRedactionFlag() + "  content size:  " + annotationContent.length);

               
                 
                                  

                   String jsonString = new Gson().toJson(annotation);
                   GenericUrl saveAnnotationContentUrl = new GenericUrl(
                           this.alfrescoBaseUrl + "/service/integrations/snowbound/SaveAnnotationContent?alf_ticket=" + authenticationTicket);
                   postJsonHttpRequest(saveAnnotationContentUrl, jsonString.getBytes());
                   
              
                     
               }
           }
           else{
               return null;
           }
       }
       catch (Exception e){
      	logger.printStackTrace(e);
       throw new FlexSnapSIAPIException("cannot save annotation " + e.toString());
       }
        
       return new ContentHandlerResult();
   }
    
    /** 
     * @see com.snowbound.snapserv.servlet.DocumentNotesInterface#getNotesContent(com.snowbound.snapserv.servlet.ContentHandlerInput)
     */
    public ContentHandlerResult getNotesContentFile(ContentHandlerInput input)
        throws FlexSnapSIAPIException
    {
        String clientInstanceId = input.getClientInstanceId();
        String documentKey = input.getDocumentId();
     // Logger.getInstance().log(Logger.FINEST,
      //                         "getNotesContent: clientInstanceId "
       //                            + clientInstanceId);
        String bookmarkFilename = documentKey + ".notes.xml";
        String fullFilePath = gFilePath + bookmarkFilename;
      //Logger.getInstance().log(Logger.FINEST,
       //                        "Retrieving notes file: " + fullFilePath);
        try
        {
            File file = new File(fullFilePath);
            byte[] bytes = ClientServerIO.getFileBytes(file);
            ContentHandlerResult result = new ContentHandlerResult();
            result.put(ContentHandlerResult.KEY_NOTES_CONTENT, bytes);
            return result;
        }
        catch (Exception e)
        {
            return null;
        }
    }
    
    /**
     * @param request
     * @param clientInstanceId
     * @param documentKey
     * @param data
     * @return
     */
     private ContentHandlerResult saveNotesContentFile(HttpServletRequest request,
                                                  String clientInstanceId,
                                                  String documentId,
                                                  byte[] data)
    {
        if (data == null)
        {
            return null;
        }
      //Logger.getInstance().log(Logger.FINEST,
                             //  "saveNotesContent..." + documentId
                               //    + "clientInstanceId: " + clientInstanceId);
        String fullFilePath = gFilePath + documentId + ".notes.xml";
        File file = new File(fullFilePath);
        try
        {
            ClientServerIO.saveFileBytes(data, file);
        }
        catch (Exception e)
        {
            Logger.getInstance().printStackTrace(e);
        }
        return new ContentHandlerResult();
    }  
    public ContentHandlerResult getNotesPermissions(ContentHandlerInput input)
            throws FlexSnapSIAPIException
        {
            ContentHandlerResult result = new ContentHandlerResult();
            result.put(ContentHandlerResult.KEY_NOTES_PERMISSIONS, "PERM_DELETE");
            return result;
        }

        /** 
         * @see com.snowbound.snapserv.servlet.DocumentNotesInterface#getNotesTemplates(com.snowbound.snapserv.servlet.ContentHandlerInput)
         */
        public ContentHandlerResult getNotesTemplates(ContentHandlerInput input)
            throws FlexSnapSIAPIException
        {
            /* FIXME - This should read from some local text or xml files. */
            ContentHandlerResult result = new ContentHandlerResult();
            Vector<NotesTemplate> vTemplates = new Vector<NotesTemplate>();
            NotesTemplate template1 = new NotesTemplate("Sample",
                                                        "This is a sample");
            NotesTemplate template2 = new NotesTemplate("Approve",
                                                        "I approve this document!");
            vTemplates.add(template1);
            vTemplates.add(template2);
            result.put(ContentHandlerResult.KEY_NOTES_TEMPLATES, vTemplates);
            return result;
        }


    public ContentHandlerResult saveBookmarkContent(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
     //    logger.log(  "Entering saveBookmarkContent method...");

        String documentID = contentHandlerInput.getDocumentId();
        byte[] bookmarkContent = contentHandlerInput.getBookmarkContent();
        


        ContentHandlerResult contentHandlerResult = new ContentHandlerResult();
        if (bookmarkContent == null)
        {
            return null;
        }
        else{
            Bookmark myBookmark = new Bookmark();
            myBookmark.setParentNodeRef( "workspace://SpacesStore/" + nodeRef);
            myBookmark.setName(documentID + ".bookmarks.xml");
            myBookmark.setContent(bookmarkContent);

            if(isNullOrEmpty(annotationHashMap)){
                myBookmark.setId("");
            }
            else{
                myBookmark.setId(bookmark.getId());
            }
            String jsonString = new Gson().toJson(myBookmark);
            GenericUrl saveBookmarkContentUrl = new GenericUrl(
                    this.alfrescoBaseUrl + "/service/integrations/snowbound/SaveBookmarkContent?alf_ticket=" + authenticationTicket);
            postJsonHttpRequest(saveBookmarkContentUrl, jsonString.getBytes());

            contentHandlerResult.put(ContentHandlerResult.DOCUMENT_ID_TO_RELOAD, documentID);
        }


        
        return contentHandlerResult;
    }
    
    public ContentHandlerResult saveNotesContent(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        //    logger.log(  "Entering saveBookmarkContent method...");
    	 ContentHandlerResult contentHandlerResult = new ContentHandlerResult();
    	 
    	 
    	 
    	try{
           String documentId= contentHandlerInput.getDocumentId();
           documentId =  URLDecoder.decode(documentId);
           byte[] noteContent = contentHandlerInput.getNotesContent();
           
          
          // System.out.println( "noderef: " + nodeRef);
          
           if (noteContent == null)
           {   //System.out.println("notes is null!!! ");
               return null;
               
           }
           else{
               Note myNote = new Note();
               myNote.setParentNodeRef( "workspace://SpacesStore/" + documentId);
               myNote.setName(nodeRef + ".note.xml");
               myNote.setContent(noteContent);
              // myNote.setId("workspace://SpacesStore/" + documentId);
               
               myNote.setId("");
              
            
               String jsonString = new Gson().toJson(myNote);
               
              
               
               GenericUrl saveNotesContentUrl = new GenericUrl(
                       this.alfrescoBaseUrl + "/service/integrations/snowbound/SaveNoteContent?alf_ticket=" + authenticationTicket);
               
            //   System.out.println("notes url: " + saveNotesContentUrl.toString()); 
               
             
               
               postJsonHttpRequest(saveNotesContentUrl, jsonString.getBytes());
          
            }
           
    	}
               catch (Exception e){
                	logger.log(  "Failed to save notes content: " + e.getMessage());
                	throw new FlexSnapSIAPIException("  Alfresco Notes could not be saved " + e.getMessage());
                }

              
          


           
           return contentHandlerResult;
       }  

    public ContentHandlerResult deleteAnnotation(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
     //   logger.log(  "Entering deleteAnnotation method...");

       // String clientID = contentHandlerInput.getClientInstanceId();
        String documentID = contentHandlerInput.getDocumentId();
        String annotationName = contentHandlerInput.getAnnotationId();
         


        try{
            Annotation annotation = annotationHashMap.get(annotationName);

            String jsonString = new Gson().toJson(annotation);
            GenericUrl deleteAnnotationUrl = new GenericUrl(
                    this.alfrescoBaseUrl + "/service/integrations/snowbound/DeleteAnnotation?alf_ticket=" + authenticationTicket);
            postJsonHttpRequest(deleteAnnotationUrl, jsonString.getBytes());
        }
        catch (Throwable e){
             logger.log(  "Error deleting layer " + annotationName + " : " + e.getMessage());
        }
        return null;
    }
    
    
   
   
   
    
     private void deleteAnnotationLayer(String annotationId )
            throws FlexSnapSIAPIException {
    	 
    	 
    	
    
    
    
 

    try{
       
        
        Annotation annotation = new Annotation();
     //   annotation.setId(id)
        annotation.setId(annotationId);
       
        
        
        
        String jsonString = new Gson().toJson(annotation);
        GenericUrl deleteAnnotationUrl = new GenericUrl(
                this.alfrescoBaseUrl + "/service/integrations/snowbound/DeleteAnnotation?alf_ticket=" + authenticationTicket);
      //  System.out.print( deleteAnnotationUrl.toString());
        postJsonHttpRequest(deleteAnnotationUrl, jsonString.getBytes());
        
       
        // sendHttpRequest(deleteAnnotationUrl).parseAsString();
    }
    catch (Throwable e){
         logger.log(  "Error deleting by Id " + annotationId + " : " + e.getMessage());
    }
    	 
    
    }
  
    
    public boolean hasAnnotations(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
         //Logger.getInstance().log(Logger.FINEST, "Entering hasAnnotations method...");

        return isNullOrEmpty(annotationHashMap);
    }

    public ContentHandlerResult getClientPreferencesXML(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
      //  logger.log(  "Entering getClientPreferencesXML method...");

        ContentHandlerResult result = new ContentHandlerResult();
        try{
            GenericUrl preferenceXMLUrl = new GenericUrl(
                    this.alfrescoBaseUrl +
                            "/service/integrations/snowbound/GetBookmark?alf_ticket=" +
                            authenticationTicket +
                            "&nodeRef=" +  "workspace://SpacesStore/" + nodeRef  +
                            "&format=json");

            String preferenceXMJsonResponse = sendHttpRequest(preferenceXMLUrl).parseAsString();
            preferenceXML = new Gson().fromJson(preferenceXMJsonResponse, PreferenceXML.class);
            String xmlString = new String(preferenceXML.getContent());
            
         //   System.out.print("preferences: " + xmlString);

            result.put(ContentHandlerResult.KEY_CLIENT_PREFERENCES_XML, xmlString);
        }
        catch (Exception e){
            logger.log(  "Failed to get client preferences XML: " + e.getMessage());

            return null;
        }
       
        return result;

    }
   
   
    public void removeDuplicateLayers(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
    	
    	 this.getAnnotationNames(contentHandlerInput);
    
    	 
    	        
    	        for(Object key1:this.annotationHashMap.keySet()){  
    	              
    	            for(Object key2:this.annotationHashMap.keySet()){  
    	               
    	                   Annotation x=this.annotationHashMap.get(key1);  
    	                   Annotation y=this.annotationHashMap.get(key2);  
    	                  
    	                   if(x.id != y.id){
    	                    if(x.name.equalsIgnoreCase(y.name)){  
    	                     
    	                      String contentString1 = new String(x.getContent());
    	       	        	  String contentString2 = new String(y.getContent());
    	       	         
    	       	           if(contentString1.equalsIgnoreCase(contentString2)){
    	       	           this.deleteAnnotationLayer(y.id);
    	       	           } 
    	                     
    	                   }
    	                }  
    	                  
    	            }  
    	              
    	              
    	        }  

    	    
    	   
    	
    	
    	
    }

    public ContentHandlerResult saveClientPreferencesXML(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        //logger.log(  "Entering saveClientPreferencesXML method...");

        try{
            preferenceXML.setContent(contentHandlerInput.getClientPreferencesXML().getBytes());
            String jsonString = new Gson().toJson(preferenceXML);

            GenericUrl savePreferenceXMLContentUrl = new GenericUrl(
                    this.alfrescoBaseUrl + "/service/integrations/snowbound/SavePreferenceXMLContent?alf_ticket=" + authenticationTicket);
            
            postJsonHttpRequest(savePreferenceXMLContentUrl, jsonString.getBytes());
        }
        catch (Exception e){

        }

        return ContentHandlerResult.VOID;
    }

    public ContentHandlerResult sendDocumentContent(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
       //logger.log(  "Entering sendDocumentContent method...");

        // This method can be changed to save the document to their desired target directory.
	     ContentHandlerResult retVal = new ContentHandlerResult();
	     //HttpServletRequest request = contentHandlerInput.getHttpServletRequest();
	     //String clientInstanceId = contentHandlerInput.getClientInstanceId();
	     //String documentKey = contentHandlerInput.getDocumentId();
	     boolean mergeAnnotations = contentHandlerInput.mergeAnnotations();
	     byte[] data = contentHandlerInput.getDocumentContent();
	     File saveFile;
		try {
			saveFile = new File(gFilePath + "sendDocument-" + URLDecoder.decode(document.getName(), "UTF-8") );
			 ClientServerIO.saveFileBytes(data, saveFile);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	     
	    
	     return retVal;

    }

    public ContentHandlerResult publishDocument(ContentHandlerInput contentHandlerInput) throws FlexSnapSIAPIException {
        // logger.log(  "Entering publishDocument method...");

        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public HttpRequestFactory getRequestFactory() {
        if (this.requestFactory == null) {
            this.requestFactory = HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
                public void initialize(HttpRequest request) throws IOException {
                    request.setParser(new JsonObjectParser(new JacksonFactory()));
                }
            });
        }
        return this.requestFactory;
    }

    private HttpResponse sendHttpRequest(GenericUrl getUrl){
        HttpResponse response = null;
        try {
            HttpRequest request = getRequestFactory().buildGetRequest(getUrl);
            response = request.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return response;
    }
    
    
    private int checkAlfrescoPermission(Annotation getAnnotation){
    	 
    	//  code to set Alfresco permissions by  annotation node 
      
          int PERM_HIDDEN = 0;
       // int PERM_REDACTION = 10;
       // int PERM_PRINT_WATERMARK = 20;
       // int PERM_VIEW_WATERMARK = 30;
       // int PERM_VIEW = 40;
          int PERM_PRINT = 50;
       // int PERM_CREATE = 60;
          int PERM_EDIT = 70;
          int PERM_DELETE = 80;
        
       
       
         if(getAnnotation.deletePermissionFlag){return PERM_DELETE;}
        
         else if(getAnnotation.editPermissionFlag){return PERM_EDIT;}
         
         else if(getAnnotation.readPermissionFlag){return PERM_PRINT;}
         
         else {return PERM_HIDDEN;}
         
             
       
        
        
     }
    
    private int checkAlfrescoPermissionGroups(  String owner,String currentUser ){
   	 
        // Sample to set Alfresco permissions by the current user's groups instead of annotation node permission
        // user can create their own rules for permission by groups
        // Need to uncomment 
    	// 1. alternative permission check snippet in getAnnotationsProperties method
    	// 2. alternative permission check snippet in getDocumentContent method 
       
     	
         boolean consumer = false;
         boolean collaborator = false;
         boolean contributor = false;
         boolean manager = false;
          
            int PERM_HIDDEN = 0;
         // int PERM_REDACTION = 10;
         // int PERM_PRINT_WATERMARK = 20;
         // int PERM_VIEW_WATERMARK = 30;
         // int PERM_VIEW = 40;
            int PERM_PRINT = 50;
         // int PERM_CREATE = 60;
            int PERM_EDIT = 70;
            int PERM_DELETE = 80;
              
                Iterator<String> authLoop = groupSet.iterator();
       
               while(authLoop.hasNext()){
               String auth = (String)authLoop.next();
                      auth = auth.toLowerCase();
                if(auth.contains("group")){
              	 if(auth.contains("consumer")){
              		 consumer=true;
              	 }else if(auth.contains("contributor")){
              		 contributor=true;
              	 }else if(auth.contains("collaborator")){
              		 collaborator=true;
              	 }else if(auth.contains("manager") || auth.contains("alfresco_administrators")){
              		 manager=true;
              	 }else{consumer=true;}
              	 
                }
               }
         //------------------------------------    
              	 
        
          if(manager){return PERM_DELETE;}
          
          else if(collaborator){return PERM_EDIT;}
          else if(contributor){
               if(currentUser.equalsIgnoreCase(owner)){
          		    return PERM_EDIT;
          	     	}else{      	     		
          	     		return PERM_PRINT;	
          	     	}
          		}
          else if(consumer){return PERM_PRINT;}
          
          else{return PERM_HIDDEN;}
          
         
        
  		 
      }
    
    public void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ex) {
            	  logger.log("Failed to close Input Stream"); 
        }
    }
}
   
    
    private void postHttpRequest(GenericUrl postUrl, byte[] data){
        try {

            HttpContent body = new ByteArrayContent("application/json", data);
                                
            HttpRequest request = getRequestFactory().buildPostRequest(postUrl, body);
            request.setConnectTimeout(120000);
            request.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
  
    
    private HttpResponse postJsonHttpRequestReturn(GenericUrl postUrl, byte[] jsonContent){
    	  HttpResponse response = null;
        try {

            HttpContent body = new ByteArrayContent("application/json", jsonContent);
            HttpRequest request = getRequestFactory().buildPostRequest(postUrl, body);
            request.setConnectTimeout(120000);
            response = request.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return response;
    }
  
    private void postJsonHttpRequest(GenericUrl postUrl, byte[] jsonContent){
        try {

            HttpContent body = new ByteArrayContent("application/json", jsonContent);
            HttpRequest request = getRequestFactory().buildPostRequest(postUrl, body);
            request.setConnectTimeout(120000);
            request.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    
    private static boolean isNullOrEmpty(final Map<String,Annotation> annotationHashMap){
        return annotationHashMap == null || annotationHashMap.isEmpty();
    }
    
  
}
