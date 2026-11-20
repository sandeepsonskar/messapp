package com.vsipl.messscanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Bundle;
import android.text.InputType;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.WindowManager;
import android.widget.EditText;

import java.security.MessageDigest;

/**
 * Mess Scanner - supervisor-only wrapper around the mess web app.
 *  - opens only your mess server (other sites are blocked)
 *  - allows the camera for the QR scanner
 *  - accepts the server's self-signed certificate (trusted on first use, then pinned)
 *  - tells the server it is the app, so student logins are refused
 */
public class MainActivity extends Activity {

    // >>> EDIT THIS to your server's HTTPS address before building <<<
    private static final String DEFAULT_SERVER = "https://10.2.4.22:5443";

    private static final String UA_TAG = " MessSupervisorApp/1.0";
    private static final int REQ_CAMERA = 77;

    private WebView web;
    private SharedPreferences prefs;
    private PermissionRequest pendingPermission;
    private boolean dialogOpen = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("mess", MODE_PRIVATE);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + UA_TAG);
        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
        web.loadUrl(server());
    }

    private String server() {
        String u = prefs.getString("server", DEFAULT_SERVER);
        return u == null || u.isEmpty() ? DEFAULT_SERVER : u;
    }

    private String serverHost() {
        String h = Uri.parse(server()).getHost();
        return h == null ? "" : h;
    }

    // ---------------------------------------------------------------- web client
    private class Client extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String host = request.getUrl().getHost();
            return host == null || !host.equals(serverHost());      // true = block other sites
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            String host = Uri.parse(error.getUrl()).getHost();
            if (host == null || !host.equals(serverHost())) {
                handler.cancel();
                return;
            }
            String fp = fingerprint(error.getCertificate());
            if (fp == null) {
                handler.proceed();
                return;
            }
            String pinned = prefs.getString("pin", null);
            if (pinned == null) {                                   // first use: remember this certificate
                prefs.edit().putString("pin", fp).apply();
                handler.proceed();
            } else if (pinned.equals(fp)) {
                handler.proceed();
            } else {
                handler.cancel();
                showCertChanged();
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                showConnectError();
            }
        }
    }

    // ---------------------------------------------------------------- camera for the web page
    private class Chrome extends WebChromeClient {
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> {
                String host = request.getOrigin() == null ? "" : request.getOrigin().getHost();
                boolean wantsCamera = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCamera = true;
                }
                if (!wantsCamera || host == null || !host.equals(serverHost())) {
                    request.deny();
                    return;
                }
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    pendingPermission = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && pendingPermission != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else {
                pendingPermission.deny();
            }
            pendingPermission = null;
        }
    }

    // ---------------------------------------------------------------- dialogs
    private void showConnectError() {
        if (dialogOpen) return;
        dialogOpen = true;
        new AlertDialog.Builder(this)
                .setTitle("Cannot connect")
                .setMessage("Could not reach the mess server:\n" + server()
                        + "\n\nCheck that the phone is on the mess network.")
                .setCancelable(false)
                .setPositiveButton("Retry", (d, w) -> { dialogOpen = false; web.loadUrl(server()); })
                .setNeutralButton("Change address", (d, w) -> { dialogOpen = false; askServer(); })
                .setNegativeButton("Exit", (d, w) -> finish())
                .show();
    }

    private void showCertChanged() {
        if (dialogOpen) return;
        dialogOpen = true;
        new AlertDialog.Builder(this)
                .setTitle("Server certificate changed")
                .setMessage("The server's security certificate is different from the one this app trusted.\n\n"
                        + "If the admin changed the server or its IP address, tap Trust new. "
                        + "Otherwise do not continue.")
                .setCancelable(false)
                .setPositiveButton("Trust new", (d, w) -> {
                    dialogOpen = false;
                    prefs.edit().remove("pin").apply();
                    web.loadUrl(server());
                })
                .setNegativeButton("Exit", (d, w) -> finish())
                .show();
    }

    private void askServer() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(server());
        new AlertDialog.Builder(this)
                .setTitle("Server address")
                .setMessage("Example: https://10.2.4.22:5443")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("Save", (d, w) -> {
                    String u = input.getText().toString().trim();
                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
                    prefs.edit().putString("server", u).remove("pin").apply();
                    web.loadUrl(server());
                })
                .setNegativeButton("Cancel", (d, w) -> web.loadUrl(server()))
                .show();
    }

    // ---------------------------------------------------------------- helpers
    private static String fingerprint(SslCertificate cert) {
        try {
            byte[] der = SslCertificate.saveState(cert).getByteArray("x509-certificate");
            if (der == null) return null;
            byte[] h = MessageDigest.getInstance("SHA-256").digest(der);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
