/*
 *  GeoBatch - Open Source geospatial batch processing system
 *  http://geobatch.codehaus.org/
 *  Copyright (C) 2007-2008-2009 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 *
 *  GPLv3 + Classpath exception
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */



package it.geosolutions.geobatch.flow.event.action.geoserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Alessio Fabiani
 * 
 */
public class GeoServerRESTHelper {
    /**
     *
     */
    private static final Logger LOGGER = Logger.getLogger(GeoServerRESTHelper.class.toString());

    /**
     * 
     * @param geoserverREST_URL
     * @param inputStream
     * @param geoserverUser
     * @param geoserverPassword
     * @return
     */
    public static boolean putBinaryFileTo(URL geoserverREST_URL, InputStream inputStream,
            String geoserverUser, String geoserverPassword, final String[] returnedLayerName) {
        boolean res = false;

        try {
            HttpURLConnection con = (HttpURLConnection) geoserverREST_URL.openConnection();
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestMethod("PUT");
            
            final String login = geoserverUser;
            final String password = geoserverPassword;

            if ((login != null) && (login.trim().length() > 0)) {
                Authenticator.setDefault(new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(login, password.toCharArray());
                    }
                });
            }

            OutputStream outputStream = con.getOutputStream();
            copyInputStream(inputStream, outputStream);
            
            final int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStreamReader is = new InputStreamReader(con.getInputStream());
                String response = readIs(is);
                is.close();
                LOGGER.info("HTTP OK: " + response);
                res = true;
            } else if (responseCode == HttpURLConnection.HTTP_CREATED){
                InputStreamReader is = new InputStreamReader(con.getInputStream());
                String response = readIs(is);
                is.close();
                final String name = extractName(response);
                extractContent(response, returnedLayerName);
//              if (returnedLayerName!=null && returnedLayerName.length>0)
//              	returnedLayerName[0]=name;
                if (LOGGER.isLoggable(Level.FINE))
                    LOGGER.log(Level.FINE,"HTTP CREATED: " + response);
                else{
                    LOGGER.info("HTTP CREATED: " + name);
                }
                res = true;
            } else {
                LOGGER.info("HTTP ERROR: " + con.getResponseMessage());
                res = false;
            }
        } catch (MalformedURLException e) {
            LOGGER.info("HTTP ERROR: " + e.getLocalizedMessage());
            res = false;
        } catch (IOException e) {
            LOGGER.info("HTTP ERROR: " + e.getLocalizedMessage());
            res = false;
        }
        return res;

    }
    
    public static boolean putBinaryFileTo(URL geoserverREST_URL, InputStream inputStream,
            String geoserverUser, String geoserverPassword) {
    	return putBinaryFileTo(geoserverREST_URL, inputStream, geoserverUser, geoserverPassword, null);
    	
    }

    /**
     * 
     * @param geoserverREST_URL
     * @param inputStream
     * @param geoserverPassword
     * @param geoserverUser
     * @return
     */
    public static boolean putTextFileTo(URL geoserverREST_URL, InputStream inputStream,
            String geoserverPassword, String geoserverUser, final String[] returnedLayerName) {
        boolean res = false;

        try {
            HttpURLConnection con = (HttpURLConnection) geoserverREST_URL.openConnection();
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestMethod("PUT");
//            con.setRequestProperty("Content-Type", "text/xml") ;

            final String login = geoserverUser;
            final String password = geoserverPassword;

            if ((login != null) && (login.trim().length() > 0)) {
                Authenticator.setDefault(new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(login, password.toCharArray());
                    }
                });
            }

            InputStreamReader inReq = new InputStreamReader(inputStream);
            OutputStreamWriter outReq = new OutputStreamWriter(con.getOutputStream());
            char[] buffer = new char[1024];
            int len;

            while ((len = inReq.read(buffer)) >= 0)
                outReq.write(buffer, 0, len);

            outReq.flush();
            outReq.close();
            inReq.close();
            
            final int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStreamReader is = new InputStreamReader(con.getInputStream());
                String response = readIs(is);
                is.close();
                LOGGER.info("HTTP OK: " + response);
                res = true;
            } else if (responseCode == HttpURLConnection.HTTP_CREATED){
                InputStreamReader is = new InputStreamReader(con.getInputStream());
                String response = readIs(is);
                is.close();
                final String name = extractName(response);  
                extractContent(response, returnedLayerName);
//              if (returnedLayerName!=null && returnedLayerName.length>0)
//            		returnedLayerName[0]=name;
                if (LOGGER.isLoggable(Level.FINE))
                    LOGGER.log(Level.FINE,"HTTP CREATED: " + response);
                else
                    LOGGER.info("HTTP CREATED: " + name);
                res = true;
            } else {
                LOGGER.info("HTTP ERROR: " + con.getResponseMessage());
                res = false;
            }
        } catch (MalformedURLException e) {
            LOGGER.info("HTTP ERROR: " + e.getLocalizedMessage());
            res = false;
        } catch (IOException e) {
            LOGGER.info("HTTP ERROR: " + e.getLocalizedMessage());
            res = false;
        } finally {
            return res;
        }
    }
    
    public static boolean putTextFileTo(URL geoserverREST_URL, InputStream inputStream,
            String geoserverPassword, String geoserverUser) {
    	return putTextFileTo(geoserverREST_URL, inputStream, geoserverPassword, geoserverUser,null);
    }

    /**
     * 
     * @param geoserverREST_URL
     * @param content
     * @param geoserverUser
     * @param geoserverPassword
     * @return
     */
    public static boolean putContent(URL geoserverREST_URL, String content, String geoserverUser,
            String geoserverPassword, final String[] returnedLayerName) {
        boolean res = false;

        try {
            HttpURLConnection con = (HttpURLConnection) geoserverREST_URL.openConnection();
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestMethod("PUT");
//            con.setRequestProperty("Content-Type", "text/xml") ;

            final String login = geoserverUser;
            final String password = geoserverPassword;

            if ((login != null) && (login.trim().length() > 0)) {
                Authenticator.setDefault(new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(login, password.toCharArray());
                    }
                });
            }

            OutputStreamWriter outReq = new OutputStreamWriter(con.getOutputStream());
            outReq.write(content);
            outReq.flush();
            outReq.close();

            final int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStreamReader is = new InputStreamReader(con.getInputStream());
                String response = readIs(is);
                is.close();
                LOGGER.info("HTTP OK: " + response);
                res = true;
            } else if (responseCode == HttpURLConnection.HTTP_CREATED){
                InputStreamReader is = new InputStreamReader(con.getInputStream());
                String response = readIs(is);
                is.close();
                final String name = extractName(response);
                extractContent(response, returnedLayerName);
//                if (returnedLayerName!=null && returnedLayerName.length>0)
//                	returnedLayerName[0]=name;
                if (LOGGER.isLoggable(Level.FINE))
                    LOGGER.log(Level.FINE,"HTTP CREATED: " + response);
                else
                    LOGGER.info("HTTP CREATED: " + name);
                res = true;
            } else {
                LOGGER.info("HTTP ERROR: " + con.getResponseMessage());
                res = false;
            }
        } catch (MalformedURLException e) {
            LOGGER.info("HTTP ERROR: " + e.getLocalizedMessage());
            res = false;
        } catch (IOException e) {
            LOGGER.info("HTTP ERROR: " + e.getLocalizedMessage());
            res = false;
        }
        return res;
    }
    
    public static boolean putContent(URL geoserverREST_URL, String content, String geoserverUser,
            String geoserverPassword) {
    	return putContent(geoserverREST_URL, content, geoserverUser, geoserverPassword, null);
    }

    // ////////////////////////////////////////////////////////////////////////
    //
    // HELPER METHODS
    //
    // ////////////////////////////////////////////////////////////////////////

    private static String extractName(final String response) {
        String name ="";
        if (response!=null && response.trim().length()>0){
            final int indexOfNameStart = response.indexOf("<name>");
            final int indexOfNameEnd = response.indexOf("</name>");
            try {
            	name = response.substring(indexOfNameStart+6, indexOfNameEnd);
            } catch (StringIndexOutOfBoundsException e) {
            	name = response;
            }
        }
        return name;
    }
    /**
     * 
     * @param response
     * @param result will contain the following elements:
     * 	result[0]: the store name
     *  result[1]: the namespace
     *  result[2]: the layername
     */
    private static void extractContent(final String response, final String[] result) {
        if (response!=null && response.trim().length()>0){
            final int indexOfName1Start = response.indexOf("<name>");
            final int indexOfName1End = response.indexOf("</name>");
            final int indexOfName2Start = response.indexOf("<name>",indexOfName1Start+1);
            final int indexOfName2End = response.indexOf("</name>",indexOfName2Start+1);
            final int indexOfWorkspaceStart = response.indexOf("<workspace>");
            try{
	            if (indexOfName1Start < indexOfWorkspaceStart){
	            	result[2]= response.substring(indexOfName1Start+6, indexOfName1End);
	            	result[1]= response.substring(indexOfName2Start+6, indexOfName2End);
	            }
	            else {
	            	result[1]= response.substring(indexOfName1Start+6, indexOfName1End);
	            	result[2]= response.substring(indexOfName2Start+6, indexOfName2End);
	            }
            
            } catch (StringIndexOutOfBoundsException e) {
            	
            }
        }
    }

    /**
     * 
     * @param in
     * @param out
     */
    private static void copyInputStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0)
            out.write(buffer, 0, len);

        in.close();
        out.flush();
        out.close();
    }

    /**
     * 
     * @param is
     * @return
     */
    private static String readIs(InputStreamReader is) {
        char[] inCh = new char[1024];
        StringBuffer input = new StringBuffer();
        int r;

        try {
            while ((r = is.read(inCh)) > 0) {
                input.append(inCh, 0, r);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return input.toString();
    }
}
