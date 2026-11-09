package com.iptvdesktop.firetv;
import android.app.AlertDialog;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=25)
public class TvActivityTest {
    @Test public void remoteControlsAndBackWorkWithoutTouchscreen() {
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("updates",0).edit().putLong("lastSuccessfulCheck",System.currentTimeMillis()).commit();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create().start().resume().visible()) {
            MainActivity activity=controller.get();assertFalse(activity.findViewById(R.id.last).isEnabled());assertTrue(activity.findViewById(R.id.settings).isFocusable());assertTrue(activity.findViewById(R.id.updates).isFocusable());
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU));assertEquals("Play another channel first.",ShadowToast.getTextOfLatestToast());
            activity.findViewById(R.id.fullscreen).performClick();assertEquals(View.GONE,activity.findViewById(R.id.sidebar).getVisibility());activity.onBackPressed();assertEquals(View.VISIBLE,activity.findViewById(R.id.sidebar).getVisibility());
            activity.findViewById(R.id.settings).performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(dialog.isShowing());dialog.dismiss();
        }
    }
}
