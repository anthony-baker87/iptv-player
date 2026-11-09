package com.iptvdesktop.firetv;
import android.content.pm.Signature;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=25)
public class UpdateServiceTest {
    @Test public void signingIdentityMustMatchAndCannotBeEmpty() {
        Signature first=new Signature(new byte[]{1,2,3}),second=new Signature(new byte[]{4,5,6});
        assertTrue(UpdateService.sameSigners(new Signature[]{first},new Signature[]{new Signature(new byte[]{1,2,3})}));
        assertFalse(UpdateService.sameSigners(new Signature[]{first},new Signature[]{second}));
        assertFalse(UpdateService.sameSigners(null,new Signature[]{first}));
        assertFalse(UpdateService.sameSigners(new Signature[]{},new Signature[]{}));
        assertFalse(UpdateService.sameSigners(new Signature[]{first},new Signature[]{first,second}));
    }
}
