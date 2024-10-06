package launchserver.auth.provider;

import launcher.helper.CommonHelper;
import launcher.helper.IOHelper;
import launcher.helper.SecurityHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RequestAuthProvider extends AuthProvider {
    private final String url;
    private final Pattern response;

    RequestAuthProvider(BlockConfigEntry block) {
        super(block);
        url = block.getEntryValue("url", StringConfigEntry.class);
        response = Pattern.compile(block.getEntryValue("response", StringConfigEntry.class));

        // Verify is valid URL
        IOHelper.verifyURL(getFormattedURL("urlAuthLogin", "urlAuthPassword", "urlAuthIP"));
    }

    public static void main(String[] a) throws IOException {

        {
            String currentResponse = IOHelper.request(new URL("https://tfc.su:3001/account/check?login=%login%&password=%password%&X-Launcher-Request=qfj45k90lagWJkl5ksH@JP$P3h3jhFC"));
            System.out.println(currentResponse);
        }

        String currentResponse = IOHelper.request(new URL("https://tfc.su:3001/account/check?login=%login%&password=%password%&X-Launcher-Request=qfj45k90lagWJkl5ksH@JP$P3h3jhFC"));
        System.out.println(currentResponse);
    }

    private HostnameVerifier prev1;
    private SSLSocketFactory prev2;

    private HostnameVerifier new1 = (arg0, arg1) -> true;
    private SSLSocketFactory new2;

    private void init() {
        if (new2 == null) {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
                        }
                    }
            };

            SSLContext sc = null;
            try {
                sc = SSLContext.getInstance("SSL");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            try {
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
            } catch (KeyManagementException e) {
                e.printStackTrace();
            }
            prev1 = HttpsURLConnection.getDefaultHostnameVerifier();
            prev2 = HttpsURLConnection.getDefaultSSLSocketFactory();

            new2 = sc.getSocketFactory();
        }
    }

    private void disableSSLCheck() {
        init();
        HttpsURLConnection.setDefaultSSLSocketFactory(new2);
        HttpsURLConnection.setDefaultHostnameVerifier(new1);

    }

    private void enableSSLCheck() {
        if (prev2 != null) {
            HttpsURLConnection.setDefaultSSLSocketFactory(prev2);
            HttpsURLConnection.setDefaultHostnameVerifier(prev1);
            prev2 = null;
            prev1 = null;
        }
    }

    @Override
    public AuthProviderResult auth(String login, String password, String ip) throws IOException {
        try {
            disableSSLCheck();
            String currentResponse = IOHelper.request(new URL(getFormattedURL(login, password, ip)));

            // Match username
            Matcher matcher = response.matcher(currentResponse);
            return matcher.matches() && matcher.groupCount() >= 1 ?
                    new AuthProviderResult(matcher.group("username"), SecurityHelper.randomStringToken()) :
                    authError(currentResponse);
        } finally {
            enableSSLCheck();
        }
    }

    @Override
    public void close() {
        // Do nothing
    }

    private String getFormattedURL(String login, String password, String ip) {
        return CommonHelper.replace(url, "login", IOHelper.urlEncode(login), "password", IOHelper.urlEncode(password), "ip", IOHelper.urlEncode(ip));
    }
}
