/**
 * @(#) CyBackupMain.java 2013. 12. 24.
 * 
 *      !@CopyRight@!
 **/
package kr.hyosang;


import java.util.Map;

import kr.hyosang.CyBackup.OnProgressListener;
import kr.hyosang.CyBackup.ProgressData;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


/**
 *
 *
 * @author 박효상
 * @version 1.0.0
 */
public class CyBackupMain
{
    private static Label progText1, progText2, progImage;
    public static void main(String[] args)
    {
        final Display display = new Display();
        final Shell shell = new Shell(display);
        shell.setText("CyBackup");
        shell.setSize(400, 300);

        int w = 300;

        Rectangle area;
        Rectangle rect = new Rectangle(5, 5, 0, 0);
        Rectangle rect2 = new Rectangle(0, 0, 0, 0);

        Group localPathGroup = new Group(shell, SWT.NONE);
        localPathGroup.setText("Step 1. 저장할 위치를 선택하세요");
        rect.width = w;
        localPathGroup.setBounds(rect);

        area = localPathGroup.getClientArea();

        final Text savePath = new Text(localPathGroup,
                SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
        savePath.setText("C:\\CyBackup");
        rect2.width = area.width - 80;
        rect2.height = 25;
        rect2.x = 5;
        rect2.y = 20;
        savePath.setBounds(rect2);

        Button browseBtn = new Button(localPathGroup, SWT.NONE);
        browseBtn.setText("변경");
        rect2.x = rect2.x + rect2.width + 5;
        rect2.width = area.width - rect2.x;
        browseBtn.setBounds(rect2);

        rect.height = (rect2.y + rect2.height) + 10;
        localPathGroup.setBounds(rect);

        rect.y += rect.height + 10;

        //두번째 그룹

        Group openLoginGroup = new Group(shell, SWT.NONE);
        openLoginGroup.setText("Step 2. 로그인을 해주세요");
        openLoginGroup.setBounds(rect);

        area = openLoginGroup.getClientArea();

        Button openLogin = new Button(openLoginGroup, SWT.NONE);
        openLogin.setText("로그인");
        rect2.x = 5;
        rect2.y = 20;
        rect2.width = area.width - rect2.x - 5;
        rect2.height = 25;
        openLogin.setBounds(rect2);

        rect.y += rect.height + 10;

        //세번째 그룹
        Group progressGroup = new Group(shell, SWT.NONE);
        progressGroup.setText("진행상태는 여기 표시됩니다");
        rect.x = rect.x + rect.width + 5;
        rect.y = 5;
        rect.width = 400;
        rect.height = 400;
        progressGroup.setBounds(rect);

        area = progressGroup.getClientArea();

        //진행상태 Text 1
        progText1 = new Label(progressGroup, SWT.NONE);
        rect2.x = 5;
        rect2.y = 20;
        rect2.width = area.width - rect2.x;
        rect2.height = 15;
        progText1.setBounds(rect2);

        //진행상태 Text2
        progText2 = new Label(progressGroup, SWT.NONE);
        rect2.y += rect2.height + 3;
        progText2.setBounds(rect2);

        //이미지 뷰어
        progImage = new Label(progressGroup, SWT.BORDER);
        rect2.y += rect2.height + 3;
        rect2.height = area.height - rect2.y;
        progImage.setBounds(rect2);

        //이벤트처리기
        browseBtn.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                DirectoryDialog dialog = new DirectoryDialog(shell);
                dialog.setFilterPath(savePath.getText());
                dialog.setMessage("백업 파일이 저장될 위치를 선택하세요");
                String folder = dialog.open();
                savePath.setText(folder);

            }
        });

        openLogin.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                LoginPopup sh = new LoginPopup(shell,
                        SWT.APPLICATION_MODAL | SWT.BORDER | SWT.TITLE | SWT.SHELL_TRIM);
                Map<String, String> cookies = sh.open();

                System.out.println("COOKIE : " + cookies);

                String qid = cookies.get("queenid");
                String cookieinfouser = cookies.get("cookieinfouser");

                System.out.println("CYWORLD ID : " + qid);
                System.out.println("COOKIE : " + cookieinfouser);

                if (qid != null && cookieinfouser != null) {
                    CyBackup backup = new CyBackup(qid, cookies, savePath.getText());
                    backup.setListener(backupListener);
                    backup.start();
                    
                    
                }else {
                    MessageBox msg = new MessageBox(shell);
                    msg.setMessage("로그인 오류");
                    msg.setText("Error");
                    msg.open();
                }
            }
        });

        shell.pack();
        rect = shell.getBounds();
        rect.width += 5;
        rect.height += 5;
        shell.setSize(rect.width, rect.height);
        shell.open();

        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        display.dispose();
    }
    
    private static OnProgressListener backupListener = new OnProgressListener() {
        @Override
        public void onProgress(ProgressData data)
        {
            final ProgressData fData = data;
            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run()
                {
                    String txt = String.format("사진첩 폴더 완료 : %d / 전체 : %d",
                            fData.completedFolderCount, fData.totalFolderCount);
                    progText1.setText(txt);

                    txt = String.format("사진첩(%s) 내 완료된 사진 : %d 건",
                            fData.workingFolderName, fData.completedPhotoCount);
                    progText2.setText(txt);

                    //이미지 처리
                    try {
                        if (!fData.lastSavedPhotoPath.endsWith("swf")) {
                            Rectangle rect = progImage.getBounds();

                            Image img = new Image(Display.getDefault(),
                                    fData.lastSavedPhotoPath);
                            Rectangle r = img.getBounds();

                            if (r.width <= rect.width && r.height <= rect.height) {
                                //not requires resize
                            }
                            else {
                                //resize
                                double ratio;
                                if (r.width > r.height) {
                                    ratio = (double) rect.width / (double) r.width;
                                    rect.height = (int) (r.height * ratio);
                                }
                                else {
                                    ratio = (double) rect.height / (double) r.height;
                                    rect.width = (int) (r.width * ratio);
                                }

                                Image res = new Image(Display.getDefault(), rect.width,
                                        rect.height);
                                GC gc = new GC(res);
                                gc.setAntialias(SWT.ON);
                                gc.setInterpolation(SWT.HIGH);
                                gc.drawImage(img, 0, 0, r.width, r.height, 0, 0,
                                        rect.width,
                                        rect.height);
                                gc.dispose();

                                img.dispose();
                                img = null;
                                img = res;
                            }

                            Image prev = progImage.getImage();
                            if (prev != null) {
                                prev.dispose();
                            }

                            progImage.setImage(img);
                        }
                    } catch (SWTException e) {
                        //이미지 처리중 오류 발생시 무시
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void onComplete()
        {
            Display.getDefault().syncExec(new Runnable() {

                @Override
                public void run()
                {
                    progText1.setText("작업 완료");
                    progText2.setText("");
                    progImage.setImage(null);
                    
                }
                
            });
            
        }
    };

}
