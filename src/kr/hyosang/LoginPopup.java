/**
 * @(#) LoginPopup.java 2013. 12. 27.
 * 
 *      !@CopyRight@!
 **/
package kr.hyosang;


import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;


/**
 *
 *
 * @author 박효상
 * @version 1.0.0
 */
public class LoginPopup extends Dialog
{
    private Shell mShell = null;
    private Browser mBrowser = null;
    private GrapCookie mRunner = null;
    private String mQueenId = null;


    /**
     * @param arg0
     * @param arg1
     */
    public LoginPopup(Shell arg0, int arg1)
    {
        super(arg0, arg1);
    }

    private final String[] cookieNames = { "C3RD",
            "ETC",
            "LOGIN",
            "MAIN",
            "MINIHP",
            "NSVC",
            "RDB",
            "SITESERVER",
            "UA3",
            "UAKD",
            "UD3",
            "ndr",
            "ndrn",
            "pcid"
    };


    public Map<String, String> open()
    {
        mShell = new Shell(getParent(), getStyle());
        mShell.setText("Login");

        mBrowser = new Browser(mShell, SWT.NONE);
        mBrowser.setBounds(mShell.getClientArea());
        mBrowser.setUrl("http://xo.nate.com/Login.sk?redirect=http%3A%2F%2Fwww.nate.com%2Fcymain%2F%3Ff%3Dnate");
        mBrowser.addLocationListener(new LocationListener() {
            @Override
            public void changed(LocationEvent arg0)
            {
                if (arg0.location.startsWith("https://cyxso.cyworld.com/natesso/slogin.jsp?q=")) {
                    System.out.println("SLOGIN : " + arg0.location);
                    mRunner = new GrapCookie(arg0.location);
                    mRunner.start();
                }
                else if (arg0.location.startsWith("http://www.nate.com/cymain/")) {
                    String source = mBrowser.getText();

                    Pattern p = Pattern.compile("CFN_link = \"([0-9]+)\"");
                    Matcher m = p.matcher(source);
                    if (m.find()) {
                        mQueenId = m.group(1);
                        System.out.println("Find : " + mQueenId);
                    }

                    mShell.close();
                }
            }


            @Override
            public void changing(LocationEvent arg0)
            {
                // TODO Auto-generated method stub

            }
        });

        mShell.open();
        Display display = getParent().getDisplay();
        while (!mShell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        if (mRunner != null) {
            while (mRunner.isAlive()) {
                try {
                    System.out.println("Cookie runner is alive");
                    Thread.sleep(100);
                } catch (Exception e) {
                }
            }
        }

        //쿠키 정보 수집
        HashMap<String, String> cookies = new HashMap<String, String>();
        cookies.put("cookieinfouser", mRunner.cookieinfouser);
        cookies.put("queenid", mQueenId);
        for (String name : cookieNames) {
            String cookie = Browser.getCookie(name, "http://q.cyworld.com");
            if (cookie != null) {
                cookies.put(name, cookie);
            }

            System.out.println(String.format("%s : %s", name, cookie));
        }

        return cookies;
    }

    class GrapCookie extends Thread
    {
        private String mUrl = null;
        public String cookieinfouser = null;


        public GrapCookie(String url)
        {
            mUrl = url;
        }


        @Override
        public void run()
        {
            //cookieuserinfo 쿠키 수집
            try {
                if (mUrl != null) {
                    URL url = new URL(mUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                    conn.setRequestMethod("GET");

                    conn.connect();

                    System.out.println("Response : " + conn.getResponseCode());
                    Map<String, List<String>> headerList = conn.getHeaderFields();
                    List<String> setCookie = headerList.get("Set-Cookie");
                    for (String c : setCookie) {
                        if (c.startsWith("cookieinfouser")) {
                            cookieinfouser = c.substring(c.indexOf("=") + 1, c.indexOf(";"));
                            System.out.println("Found : " + cookieinfouser);
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}
