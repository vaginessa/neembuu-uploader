/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package neembuu.uploader.uploaders;

import shashaank.smallmodule.SmallModule;
import neembuu.uploader.interfaces.Uploader;
import neembuu.uploader.interfaces.Account;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import neembuu.uploader.accounts.CloudSixAccount;
import neembuu.uploader.exceptions.NUException;
import neembuu.uploader.exceptions.uploaders.NUMaxFileSizeException;
import neembuu.uploader.httpclient.NUHttpClient;
import neembuu.uploader.httpclient.httprequest.NUHttpPost;
import neembuu.uploader.interfaces.UploadStatus;
import neembuu.uploader.interfaces.UploaderAccountNecessary;
import neembuu.uploader.interfaces.abstractimpl.AbstractUploader;
import neembuu.uploader.utils.NUHttpClientUtils;
import neembuu.uploader.utils.NULogger;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.util.Random;

/**
 *
 * @author Paralytic
 */
@SmallModule(
    exports={CloudSix.class,CloudSixAccount.class},
    interfaces={Uploader.class,Account.class},
    name="CloudSix.me"
)
public class CloudSix extends AbstractUploader implements UploaderAccountNecessary{
    
    CloudSixAccount cloudSixAccount = (CloudSixAccount) getAccountsProvider().getAccount("CloudSix.me");
    
    private final HttpClient httpclient = NUHttpClient.getHttpClient();
    private HttpContext httpContext = new BasicHttpContext();
    private HttpResponse httpResponse;
    private NUHttpPost httpPost;
    private String responseString;
    private Document doc;
    private String uploadURL;
    private String userType;
    private String sessionID = "";
    private String srv_tmp_url = "";
    private String uploadid_s = "";
    private String upload_fn = "";
    
    private String downloadlink = "";
    private String deletelink = "";

    public CloudSix() {
        downURL = UploadStatus.PLEASEWAIT.getLocaleSpecificString();
        delURL = UploadStatus.PLEASEWAIT.getLocaleSpecificString();
        host = "CloudSix.me";
        if (cloudSixAccount.loginsuccessful) {
            host = cloudSixAccount.username + " | CloudSix.me";
        }
        maxFileSizeLimit = 536870912L; // 512 MB
    }

    private void initialize() throws Exception {
        responseString = NUHttpClientUtils.getData("http://cloudsix.me/", httpContext);

        doc = Jsoup.parse(responseString);
        uploadURL = doc.select("form[name=file]").attr("action");
        srv_tmp_url = doc.select("form[name=file]").select("input[name=srv_tmp_url]").attr("value");
        sessionID = doc.select("form[name=file]").select("input[name=sess_id]").attr("value");
    }

    @Override
    public void run() {
        try {
            if (cloudSixAccount.loginsuccessful) {
                userType = "reg";
                httpContext = cloudSixAccount.getHttpContext();
                maxFileSizeLimit = 536870912L; // 512 MB
            } else {
                host = "CloudSix.me";
                uploadInvalid();
                return;
            }

            if (file.length() > maxFileSizeLimit) {
                throw new NUMaxFileSizeException(maxFileSizeLimit, file.getName(), host);
            }
            uploadInitialising();
            initialize();

            long uploadID;
            Random random = new Random();
            uploadID = Math.round(random.nextFloat() * Math.pow(10,12));
            uploadid_s = String.valueOf(uploadID);
            
            // http://wrk7.cloudsix.me/cgi-bin/upload.cgi?upload_id=663522723808&js_on=1&utype=reg&upload_type=file
            uploadURL += uploadid_s + "&js_on=1&utype=" + userType + "&upload_type=file";
            httpPost = new NUHttpPost(uploadURL);
            MultipartEntity mpEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
            mpEntity.addPart("upload_type", new StringBody("file"));
            mpEntity.addPart("sess_id", new StringBody(sessionID));
            mpEntity.addPart("srv_tmp_url", new StringBody(srv_tmp_url));
            mpEntity.addPart("submit_btn", new StringBody("Start Upload"));
            mpEntity.addPart("file_0", createMonitoredFileBody());
            mpEntity.addPart("file_0_descr", new StringBody("Uploaded via Neembuu Uploader!"));
            httpPost.setEntity(mpEntity);
            
            NULogger.getLogger().log(Level.INFO, "executing request {0}", httpPost.getRequestLine());
            NULogger.getLogger().info("Now uploading your file into CloudSix.com");
            uploading();
            httpResponse = httpclient.execute(httpPost, httpContext);
            responseString = EntityUtils.toString(httpResponse.getEntity());
            
            doc = Jsoup.parse(responseString);
            
            //Read the links
            gettingLink();
            upload_fn = doc.select("textarea[name=fn]").val();
            if (upload_fn != null) {
                httpPost = new NUHttpPost("http://cloudsix.me/");
                List<NameValuePair> formparams = new ArrayList<NameValuePair>();
                formparams.add(new BasicNameValuePair("fn", upload_fn));
                formparams.add(new BasicNameValuePair("op", "upload_result"));
                formparams.add(new BasicNameValuePair("st", "OK"));

                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");
                httpPost.setEntity(entity);
                httpResponse = httpclient.execute(httpPost, httpContext);
                responseString = EntityUtils.toString(httpResponse.getEntity());
                
                doc = Jsoup.parse(responseString);
                downloadlink = doc.select("textarea").first().val();
                deletelink = doc.select("textarea").eq(3).val();

                NULogger.getLogger().log(Level.INFO, "Delete link : {0}", deletelink);
                NULogger.getLogger().log(Level.INFO, "Download link : {0}", downloadlink);
                downURL = downloadlink;
                delURL = deletelink;
				
                uploadFinished();
            }
        } catch(NUException ex){
            ex.printError();
            uploadInvalid();
        } catch (Exception e) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, e);
            uploadFailed();
        }
    }   
}
