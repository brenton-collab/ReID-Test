package ca.brentzinck.fintracid;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenshotActivity extends Activity {
    public static final String EXTRA_CAPTURE_PATH = "screenshot_path";
    private static final int REQ = 5501;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private boolean captured = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        MediaProjectionManager m = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(), REQ);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ || resultCode != RESULT_OK || data == null) { setResult(RESULT_CANCELED); finish(); return; }
        Intent svc = new Intent(this, ProjectionService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        new Handler(Looper.getMainLooper()).postDelayed(() -> beginProjection(resultCode, data), 150);
    }

    private void beginProjection(int resultCode, Intent data) {
        try {
            MediaProjectionManager m = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = m.getMediaProjection(resultCode, data);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { cleanup(); }
            }, new Handler(Looper.getMainLooper()));

            WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            int width, height, density = getResources().getConfiguration().densityDpi;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Rect r = wm.getMaximumWindowMetrics().getBounds(); width = r.width(); height = r.height();
            } else {
                DisplayMetrics dm = new DisplayMetrics(); wm.getDefaultDisplay().getRealMetrics(dm); width = dm.widthPixels; height = dm.heightPixels; density = dm.densityDpi;
            }
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            final int fw=width, fh=height;
            reader.setOnImageAvailableListener(ir -> {
                if (captured) return;
                Image image = ir.acquireLatestImage(); if (image == null) return;
                captured = true;
                try {
                    Image.Plane p = image.getPlanes()[0]; ByteBuffer buf = p.getBuffer();
                    int pixelStride=p.getPixelStride(), rowStride=p.getRowStride(), rowPadding=rowStride-pixelStride*fw;
                    Bitmap wide=Bitmap.createBitmap(fw+rowPadding/pixelStride, fh, Bitmap.Config.ARGB_8888); wide.copyPixelsFromBuffer(buf);
                    Bitmap bmp=Bitmap.createBitmap(wide,0,0,fw,fh); wide.recycle();
                    File dir=new File(getFilesDir(),"relay_pending"); if(!dir.exists()) dir.mkdirs();
                    String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.CANADA).format(new Date());
                    File out=new File(dir,"screenshot_"+stamp+".png");
                    try(FileOutputStream fos=new FileOutputStream(out)){ bmp.compress(Bitmap.CompressFormat.PNG,100,fos); }
                    bmp.recycle();
                    Intent result=new Intent(); result.putExtra(EXTRA_CAPTURE_PATH,out.getAbsolutePath()); setResult(RESULT_OK,result);
                } catch(Exception e) { runOnUiThread(() -> Toast.makeText(this,"Screenshot failed: "+e.getMessage(),Toast.LENGTH_LONG).show()); setResult(RESULT_CANCELED); }
                finally { image.close(); cleanup(); runOnUiThread(this::finish); }
            }, new Handler(Looper.getMainLooper()));
            virtualDisplay = projection.createVirtualDisplay("RelayScreenshot", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
        } catch(Exception e) { Toast.makeText(this,"Screenshot failed: "+e.getMessage(),Toast.LENGTH_LONG).show(); cleanup(); finish(); }
    }

    private void cleanup() {
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay=null; }
        if (reader != null) { reader.close(); reader=null; }
        if (projection != null) { try { projection.stop(); } catch(Exception ignored){} projection=null; }
        stopService(new Intent(this, ProjectionService.class));
    }
}