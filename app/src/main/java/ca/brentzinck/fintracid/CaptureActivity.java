package ca.brentzinck.fintracid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureActivity extends ComponentActivity {
    public static final String EXTRA_CAPTURE_PATH = "capture_path";
    public static final String EXTRA_GUIDE = "capture_guide";
    public static final String EXTRA_ORIENTATION = "capture_orientation";
    public static final String EXTRA_PERSPECTIVE_GUIDE = "capture_perspective_guide";
    public static final String EXTRA_INSTRUCTION = "capture_instruction";
    private static final int CAMERA_PERMISSION = 4401;

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String orientation=getIntent().getStringExtra(EXTRA_ORIENTATION);
        if("landscape".equals(orientation)) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        else if("portrait".equals(orientation)) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        buildUi();
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera();
        else ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION);
    }

    private void buildUi() {
        FrameLayout root=new FrameLayout(this);
        previewView=new PreviewView(this);previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        String guide=getIntent().getStringExtra(EXTRA_GUIDE); if(guide==null)guide="none";
        boolean perspective=getIntent().getBooleanExtra(EXTRA_PERSPECTIVE_GUIDE,false);
        GuideOverlay overlay=new GuideOverlay(this,guide,perspective);
        root.addView(overlay,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        TextView cancel=new TextView(this);
        cancel.setText("×  Cancel"); cancel.setTextSize(16); cancel.setTextColor(Color.WHITE); cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(14),dp(8),dp(14),dp(8)); cancel.setBackgroundColor(0x99000000); cancel.setContentDescription("Cancel capture");
        cancel.setOnClickListener(v->cancelCapture());
        FrameLayout.LayoutParams xp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(48),Gravity.TOP|Gravity.START);xp.leftMargin=dp(14);xp.topMargin=dp(14);root.addView(cancel,xp);
        cancel.setOnApplyWindowInsetsListener((v,insets)->{FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)v.getLayoutParams();p.topMargin=dp(14)+insets.getSystemWindowInsetTop();v.setLayoutParams(p);return insets;});cancel.requestApplyInsets();

        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setGravity(Gravity.CENTER_HORIZONTAL);controls.setPadding(dp(20),dp(12),dp(20),dp(28));controls.setBackgroundColor(0x99000000);
        TextView hint=new TextView(this);String instruction=getIntent().getStringExtra(EXTRA_INSTRUCTION);hint.setText(instruction==null||instruction.trim().isEmpty()?"Capture clearly. Keep the full subject visible.":instruction);hint.setTextColor(Color.WHITE);hint.setTextSize(15);hint.setGravity(Gravity.CENTER);controls.addView(hint);
        if(!"none".equals(guide)){TextView guideHint=new TextView(this);guideHint.setText("Fit the subject inside the guide. The overlay is not saved into the image.");guideHint.setTextColor(0xffd8dde5);guideHint.setTextSize(12);guideHint.setGravity(Gravity.CENTER);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=dp(5);controls.addView(guideHint,gp);}
        Button shutter=new Button(this);shutter.setText("Capture");shutter.setOnClickListener(v->takePhoto());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(10);controls.addView(shutter,bp);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);root.addView(controls,cp);
        setContentView(root);
    }

    void cancelCapture(){setResult(RESULT_CANCELED);finish();}
    @Override public void onBackPressed(){cancelCapture();}

    private void startCamera(){ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);future.addListener(()->{try{ProcessCameraProvider provider=future.get();Preview preview=new Preview.Builder().build();preview.setSurfaceProvider(previewView.getSurfaceProvider());imageCapture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();provider.unbindAll();provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,imageCapture);}catch(Exception e){Toast.makeText(this,"Camera unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show();finish();}},ContextCompat.getMainExecutor(this));}

    private void takePhoto(){if(imageCapture==null)return;File dir=new File(getFilesDir(),"relay_pending");if(!dir.exists()&&!dir.mkdirs()){Toast.makeText(this,"Could not create private capture storage.",Toast.LENGTH_LONG).show();return;}String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.CANADA).format(new Date());File target=new File(dir,"camera_"+stamp+".jpg");ImageCapture.OutputFileOptions options=new ImageCapture.OutputFileOptions.Builder(target).build();imageCapture.takePicture(options,cameraExecutor,new ImageCapture.OnImageSavedCallback(){@Override public void onImageSaved(ImageCapture.OutputFileResults r){Intent result=new Intent();result.putExtra(EXTRA_CAPTURE_PATH,target.getAbsolutePath());setResult(RESULT_OK,result);finish();}@Override public void onError(ImageCaptureException e){runOnUiThread(()->Toast.makeText(CaptureActivity.this,"Capture failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}});}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==CAMERA_PERMISSION&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)startCamera();else if(requestCode==CAMERA_PERMISSION){Toast.makeText(this,"Camera permission is required for in-app capture.",Toast.LENGTH_LONG).show();finish();}}
    @Override protected void onDestroy(){super.onDestroy();cameraExecutor.shutdown();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}

    static class GuideOverlay extends View {
        final String guide; final boolean perspective; final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG); final Paint shade=new Paint(Paint.ANTI_ALIAS_FLAG);
        GuideOverlay(android.content.Context c,String g,boolean p){super(c);guide=g;perspective=p;line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(3f*c.getResources().getDisplayMetrics().density);line.setColor(0xeeffffff);shade.setColor(0x55000000);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if("none".equals(guide))return;float w=getWidth(),h=getHeight();float reserved=Math.min(h*.26f,260f*getResources().getDisplayMetrics().density);float usableH=h-reserved;RectF box;
            if("id_card".equals(guide)){float bw=Math.min(w*.86f,usableH*.72f*1.586f);float bh=bw/1.586f;if(bh>usableH*.62f){bh=usableH*.62f;bw=bh*1.586f;}box=new RectF((w-bw)/2f,(usableH-bh)/2f,(w+bw)/2f,(usableH+bh)/2f);drawShade(c,box,28f);c.drawRoundRect(box,22f,22f,line);}
            else {float bh=usableH*.68f;float bw=Math.min(w*.84f,bh*.77f);bh=bw/0.77f;if(bh>usableH*.72f){bh=usableH*.72f;bw=bh*.77f;}box=new RectF((w-bw)/2f,(usableH-bh)/2f,(w+bw)/2f,(usableH+bh)/2f);drawShade(c,box,10f);c.drawRoundRect(box,8f,8f,line);drawCorners(c,box);}
            if(perspective){Paint grid=new Paint(line);grid.setStrokeWidth(1.2f*getResources().getDisplayMetrics().density);grid.setColor(0x99ffffff);for(int i=1;i<3;i++){float x=box.left+box.width()*i/3f;c.drawLine(x,box.top,x,box.bottom,grid);float y=box.top+box.height()*i/3f;c.drawLine(box.left,y,box.right,y,grid);}float inset=box.width()*.08f;c.drawLine(box.left+inset,box.top,box.right-inset,box.bottom,grid);c.drawLine(box.right-inset,box.top,box.left+inset,box.bottom,grid);}
        }
        void drawShade(Canvas c,RectF box,float radius){Path outer=new Path();outer.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);Path inner=new Path();inner.addRoundRect(box,radius,radius,Path.Direction.CW);if(android.os.Build.VERSION.SDK_INT>=19)outer.op(inner,Path.Op.DIFFERENCE);c.drawPath(outer,shade);}
        void drawCorners(Canvas c,RectF b){float q=Math.min(b.width(),b.height())*.08f;c.drawLine(b.left,b.top,b.left+q,b.top,line);c.drawLine(b.left,b.top,b.left,b.top+q,line);c.drawLine(b.right,b.top,b.right-q,b.top,line);c.drawLine(b.right,b.top,b.right,b.top+q,line);c.drawLine(b.left,b.bottom,b.left+q,b.bottom,line);c.drawLine(b.left,b.bottom,b.left,b.bottom-q,line);c.drawLine(b.right,b.bottom,b.right-q,b.bottom,line);c.drawLine(b.right,b.bottom,b.right,b.bottom-q,line);}
    }
}