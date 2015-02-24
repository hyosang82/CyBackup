/**
 * 
 * @(#) CyBackup.java 2013. 12. 24.
 * 
 *      !@CopyRight@!
 **/
package kr.hyosang;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;


/**
 * 
 * 
 * @author 박효상
 * @version 1.0.0
 */
public class CyBackup extends Thread
{
    public static boolean bDebug = true;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.72 Safari/537.36";
    private static final String SERVER_ROOT = "http://q.cyworld.com/"; // +queen ID
    private static final String PHOTO_FOLDER_LIST = "/ajax_photoFolder_list";
    private static final String PHOTO_LIST = "/append/photo/wide/"; // +folder no
    private static final String COMMENT_MORE = "/append/photo/replyMore";

    private String mQueenId = null;
    private String mCookie = null;
    private String mRootPath = null;
    private OnProgressListener mListener = null;
    private ProgressData mListenerData = null;

    public static interface OnProgressListener
    {
        public void onProgress(final ProgressData data);
        public void onComplete();
    }

    public static class Comment
    {
        public String writer;
        public String date;
        public String content;
        public boolean bReReply = false;
    }

    public static class ProgressData
    {
        public int totalFolderCount = 0;
        public int completedFolderCount = 0;
        public int completedPhotoCount = 0;
        public String lastSavedPhotoPath = null;
        public String workingFolderName = null;

    }


    public CyBackup(String queenId, Map<String, String> cookies, String rootPath)
    {
        mQueenId = queenId;
        mRootPath = rootPath;

        // 쿠키 조립
        StringBuffer sb = new StringBuffer();
        Set<Entry<String, String>> list = cookies.entrySet();
        for (Entry<String, String> c : list) {
            sb.append(String.format("%s=%s; ", c.getKey(), c.getValue()));
        }

        mCookie = sb.toString();

        System.out.println(mCookie);
    }


    @Deprecated
    public CyBackup(String queenId, String cookieStr, String rootPath)
    {
        mQueenId = queenId;
        mCookie = cookieStr;
        mRootPath = rootPath;
    }


    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run()
    {
        // 저장위치 초기화
        File f = new File(mRootPath);
        if (!f.exists()) {
            // 생성
            f.mkdirs();
        }
        
        //로그 출력장치 변경
        String outFile = String.format("%s/cybackup.out.log", mRootPath);
        String errFile = String.format("%s/cybackup.err.log", mRootPath);
        
        try {
            PrintStream outStream = new PrintStream(outFile);
            PrintStream errStream = new PrintStream(errFile);
            
            System.setOut(outStream);
            System.setErr(errStream);
    
            if (f.canWrite()) {
                mListenerData = new ProgressData();
    
                // 사진 폴더 리스트
                try {
                    URL url = new URL(getFullUrl(PHOTO_FOLDER_LIST));
                    String jsonData = getContent(url);
    
                    String no, name;
                    JSONArray photoArr = (JSONArray) JSONValue.parse(jsonData);
                    
                    for (int i = photoArr.size() - 1; i >= 0; i--) {
                        if ((boolean) ((JSONObject) photoArr.get(i)).get("isLine")) {
                            photoArr.remove(i);
                        }
                    }
    
                    mListenerData.totalFolderCount = photoArr.size();
    
                    for (int i = 0; i < photoArr.size(); i++) {
                        JSONObject photoFolder = (JSONObject) photoArr.get(i);
    
                        if ((boolean) photoFolder.get("isLine")) {
                            continue;
                        }
    
                        no = String.valueOf(photoFolder.get("folderNo"));
                        name = (String) photoFolder.get("folderName");
    
                        processPhotoFolder(no, name, f);
    
                        mListenerData.completedFolderCount = i;
                    }
    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                System.out.println("Cannot write onto : " + f.getAbsolutePath());
            }
            
            outStream.close();
            errStream.close();
        }catch(Exception e) {
            e.printStackTrace();
        }
        
        if(mListener != null) {
            mListener.onComplete();
        }
    }


    private void processPhotoFolder(String folderNo, String folderName,
            File parentFolder)
    {
        String endSeq = null;
        String endDt = null;
        int page = 1;
        int totalCnt = 0;

        mListenerData.workingFolderName = folderName;

        // 폴더 생성
        File targetFolder = new File(String.format("%s\\%s",
                parentFolder.getAbsolutePath(), folderName));
        targetFolder.mkdir();

        while ((endSeq != null && endDt != null) || (page == 1)) {
            try {
                String url = getFullUrl(PHOTO_LIST + folderNo);

                String html = null;

                try {
                    if (endSeq != null && endDt != null) {
                        String postBody = String.format(
                                "itemSeq=%s&endDate=%s", endSeq, endDt);
                        html = getContent(new URL(url), postBody);
                    }
                    else {
                        html = getContent(new URL(url));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (html != null) {
                    int idx1, idx2;
                    idx1 = html
                            .indexOf("<article class=\"fm_c_by\" id=\"wideItem");
                    if (idx1 >= 0) {
                        idx2 = html.indexOf("</article>");

                        String title, writedate, content, fileurl;

                        Pattern titlePattern = Pattern.compile("<h1>(.*)</h1>");
                        Pattern writeDatePattern = Pattern
                                .compile("<span class=\"fmc_date\">(.*)</span>");
                        Pattern contentPattern = Pattern.compile(
                                "<div class=\"fmc_txt_in\">(.*?)</div>", Pattern.DOTALL);
                        Pattern photoPattern = Pattern.compile("<img src=\"(http[^\"]*)\"");
                        Pattern flashPattern = Pattern
                                .compile("obj_swfphoto\\('[0-9]+', '([^']+)'");

                        while (idx1 >= 0 && idx2 >= 0) {
                            try {
                                String part = html.substring(idx1, idx2);

                                Matcher m = titlePattern.matcher(part);
                                title = (m.find() ? m.group(1) : null);

                                Matcher m2 = writeDatePattern.matcher(part);
                                writedate = (m2.find() ? m2.group(1) : null);

                                Matcher m3 = contentPattern.matcher(part);
                                content = (m3.find() ? m3.group(1) : null);

                                Matcher m4 = flashPattern.matcher(part);
                                if (m4.find()) {
                                    fileurl = m4.group(1);
                                }
                                else {
                                    // 플래쉬 없음. 사진으로 처리
                                    m4 = photoPattern.matcher(part);
                                    fileurl = m4.find() ? m4.group(1) : null;
                                }

                                if (bDebug) {
                                    System.out.println(String.format(
                                            "%03d / %s / %s / %s / %s", totalCnt,
                                            title, writedate, fileurl, content));
                                }

                                // 코멘트 처리
                                ArrayList<Comment> commentList = parseComments(part);

                                totalCnt++;

                                // 파일 저장
                                if (fileurl != null) {
                                    String path = String.format("%s/%03d",
                                            targetFolder.getAbsolutePath(),
                                            totalCnt);

                                    if (fileurl.charAt(fileurl.length() - 4) == '.') {
                                        path += fileurl
                                                .substring(fileurl.length() - 4);
                                    }

                                    saveFile(fileurl, path);
                                    mListenerData.lastSavedPhotoPath = path;
                                }

                                // 정보파일 저장
                                content = content.replaceAll("<br>", "\r\n");
                                content = content.replaceAll("&nbsp;", " ");

                                StringBuffer sb = new StringBuffer();
                                sb.append(title);
                                sb.append("\r\n");
                                sb.append(writedate);
                                sb.append("\r\n");
                                sb.append(content);

                                if (commentList != null) {
                                    sb.append("\r\n\r\n");

                                    for (Comment c : commentList) {
                                        sb.append(String.format("%s (%s) : %s\r\n", c.writer,
                                                c.date, c.content));
                                    }
                                }

                                try {
                                    FileWriter fw = new FileWriter(String.format(
                                            "%s/%03d.txt",
                                            targetFolder.getAbsolutePath(),
                                            totalCnt));
                                    fw.write(sb.toString());
                                    fw.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } catch (Exception e) {
                                //continue next
                                e.printStackTrace();
                            }

                            mListenerData.completedPhotoCount = totalCnt;

                            idx1 = html.indexOf(
                                    "<article class=\"fm_c_by\" id=\"wideItem",
                                    idx2);
                            idx2 = (idx1 >= 0 ? html
                                    .indexOf("</article>", idx1) : -1);

                            if (mListener != null) {
                                mListener.onProgress(mListenerData);
                            }
                        } // 사진 loop
                    } // item check

                    Matcher pm1 = Pattern
                            .compile(
                                    "<input id=\"content_end_seq\" type=\"hidden\" value=\"([0-9]+)\">")
                            .matcher(html);
                    Matcher pm2 = Pattern
                            .compile(
                                    "<input id=\"content_end_dt\" type=\"hidden\" value=\"([0-9]+)\">")
                            .matcher(html);

                    if (pm1.find() && pm2.find()) {
                        endSeq = pm1.group(1);
                        endDt = pm2.group(1);
                    }
                    else {
                        endSeq = null;
                        endDt = null;
                    }
                }
                else {
                    endSeq = null;
                    endDt = null;
                } // content check
            } catch (Exception e) {
                e.printStackTrace();
            }

            page++;
        }

    }


    private String getFullUrl(String uri)
    {
        return String.format("%s%s%s", SERVER_ROOT, mQueenId, uri);
    }


    private String getContent(URL url) throws Exception
    {
        return getContent(url, null);
    }


    private String getContent(URL url, String postBody) throws Exception
    {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoInput(true);
        conn.setDoOutput(true);

        conn.setRequestMethod("POST");
        conn.addRequestProperty("Cookie", mCookie);
        conn.addRequestProperty("Referer", "http://q.cyworld.com/");
        conn.addRequestProperty("User-Agent", USER_AGENT);

        conn.connect();

        OutputStream os = conn.getOutputStream();
        if (postBody != null && postBody.length() > 0) {
            os.write(postBody.getBytes(Charset.forName("UTF-8")));
        }
        os.flush();

        InputStream is = conn.getInputStream();
        int nRead;
        byte[] buf = new byte[1024];
        ByteArrayOutputStream stor = new ByteArrayOutputStream();

        while ((nRead = is.read(buf)) > 0) {
            stor.write(buf, 0, nRead);
        }
        is.close();

        stor.close();

        return new String(stor.toByteArray(), Charset.forName("UTF-8"));
    }


    private void saveFile(String url, String path) throws Exception
    {
        HttpURLConnection conn = (HttpURLConnection) (new URL(url))
                .openConnection();
        conn.setDoInput(true);

        conn.setRequestMethod("GET");
        conn.addRequestProperty("Cookie", mCookie);
        conn.addRequestProperty("Referer", "http://q.cyworld.com/");
        conn.addRequestProperty("User-Agent", USER_AGENT);

        conn.connect();

        File f = new File(path);
        try {
            f.getParentFile().mkdirs();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(f);
            int nRead;
            byte[] buf = new byte[1024];

            while ((nRead = is.read(buf)) > 0) {
                fos.write(buf, 0, nRead);
            }

            fos.close();
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private ArrayList<Comment> parseComments(String part)
    {
        Pattern commentNamePattern = Pattern.compile("class=\"mc_name\">([^<]+)");
        Pattern commentNoLinkNamePattern = Pattern.compile("class=\"cmt_t_w\">([^<]+)",
                Pattern.DOTALL);
        Pattern commentDatePattern = Pattern.compile("class=\"mc_date\">([^<]+)");
        Pattern commentContentPattern = Pattern.compile("<p>(.*)<!--", Pattern.DOTALL);

        Pattern moreCheckPattern = Pattern.compile("reply\\.moreReplyList\\(([0-9]+), '([0-9]+)'");

        Pattern commentForm1 = Pattern.compile("<input .* id=\"lastReplySeq.* value=\"([0-9]+)\"");
        Pattern commentForm2 = Pattern
                .compile("<input .* id=\"lastParentReplySeq.* value=\"([0-9]+)\"");
        Pattern commentCnt = Pattern.compile("<input .* id=\"replyCnt.* value=\"([0-9]+)\"");

        // 코멘트 처리
        int cidx1, cidx2;

        boolean bMore = true;
        int loadedReply = 0;

        cidx1 = part.indexOf("<article class=\"fmc_cmt_w\"");
        if (cidx1 >= 0) {
            cidx2 = part.indexOf("</ol>", cidx1);

            //코멘트 더보기 있나 체크
            Matcher morem = moreCheckPattern.matcher(part);
            String params = null;
            if (morem.find()) {
                String replyCnt = null;

                Matcher m = commentCnt.matcher(part);
                replyCnt = (m.find() ? m.group(1) : null);

                params = String.format("itemSeq=%s&itemWriterId=%s&replyCnt=%s",
                        morem.group(1), morem.group(2), replyCnt);

                params = String.format("%s&itemReplySeq=%%s&parentReplySeq=%%s&loadedReplyCnt=%%d",
                        params);
            }

            if (cidx2 == -1) {
                //코멘트 제로
                return null;
            }

            String cont = part.substring(cidx1, cidx2);

            ArrayList<Comment> commentList = new ArrayList<Comment>();

            do {
                cidx1 = cont.indexOf("<li");

                if (cidx1 == -1)
                    break; //더 없음

                cidx2 = cont.indexOf("</li>", cidx1);

                ArrayList<Comment> pageList = new ArrayList<Comment>();

                while (cidx1 >= 0 && cidx2 >= 0) {
                    String cpart = cont.substring(cidx1, cidx2);
                    Comment c = new Comment();

                    Matcher cm1 = commentNamePattern.matcher(cpart);
                    if (cm1.find()) {
                        c.writer = cm1.group(1);
                    }
                    else {
                        // 탈퇴한 회원은 이름에 링크 없음
                        cm1 = commentNoLinkNamePattern.matcher(cpart);
                        c.writer = (cm1.find() ? cm1.group(1).trim() : null);
                    }

                    Matcher cm2 = commentDatePattern.matcher(cpart);
                    c.date = (cm2.find() ? cm2.group(1) : null);

                    Matcher cm3 = commentContentPattern.matcher(cpart);
                    c.content = (cm3.find() ? cm3.group(1).trim() : null);

                    if (c.content != null) {
                        if (c.content.endsWith("</font>")) {
                            //이게 뒤에 붙어있는경우가 있음...
                            c.content = c.content.substring(0, c.content.length() - 7);
                        }
                    }

                    if (bDebug) {
                        System.out.println(String.format("Comment : %s (%s) : %s",
                                c.writer, c.date, c.content));
                    }

                    pageList.add(c);

                    loadedReply++;

                    cidx1 = cont.indexOf("<li", cidx2);
                    cidx2 = cont.indexOf("</li>", cidx1);
                }

                commentList.addAll(0, pageList);

                // 더보기
                if (params != null) {
                    Matcher m = commentForm1.matcher(cont);
                    String replySeq = (m.find() ? m.group(1) : null);

                    m = commentForm2.matcher(cont);
                    String parentReply = (m.find() ? m.group(1) : null);

                    String p = String.format(params, replySeq, parentReply, loadedReply);

                    if (bDebug) {
                        System.out.println(String.format("댓글 더보기 : %s", p));
                    }

                    try {
                        cont = getContent(new URL(getFullUrl(COMMENT_MORE)), p);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    bMore = false;
                }
            } while (bMore);

            return commentList;
        }

        return null;
    }


    public void setListener(OnProgressListener listener)
    {
        mListener = listener;
    }

}
