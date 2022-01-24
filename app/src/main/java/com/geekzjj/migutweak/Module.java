package com.geekzjj.migutweak;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Module implements IXposedHookLoadPackage {
    private static String TAG = "MiguTweak";

    private static final int[] WINDOW_TYPES_WHERE_HOME_DOESNT_WORK = {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
    };
    static final int DOUBLE_TAP_HOME_NOTHING = 0;
    static final int DOUBLE_TAP_HOME_RECENT_SYSTEM_UI = 1;
    static final int LONG_PRESS_HOME_RECENT_SYSTEM_UI = 1;
    static final int RECENT_APPS_BEHAVIOR_SHOW_OR_DISMISS = 0;
    static final int RECENT_APPS_BEHAVIOR_EXIT_TOUCH_MODE_AND_SHOW = 1;
    static final int RECENT_APPS_BEHAVIOR_DISMISS = 2;
    static final int RECENT_APPS_BEHAVIOR_DISMISS_AND_SWITCH = 3;
    static boolean printed = false;
    static boolean homeLongPressedDown = false;
    static Context mContext;

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam)  {
        if (!lpparam.packageName.equals("android")) return;
//        Class<?> PhoneWindowManagerClass = XposedHelpers.findClass("com.android.internal.policy.impl.PhoneWindowManager",lpparam.classLoader);
//        Method[] methods = PhoneWindowManagerClass.getMethods();
//        for(Method m:methods){
//            if("interceptKeyBeforeDispatching".equals(m.getName())){
//                Type[] types = m.getGenericParameterTypes();
//                for(int i=0;i<types.length;i++){
//                    XposedBridge.log("Param["+i+"]:"+types[i]);
//                }
//            }
//        }
        XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.PhoneWindowManager",
                lpparam.classLoader, "readConfigurationDependentBehaviors",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
//                        XposedHelpers.setIntField(param.thisObject,"mDoubleTapOnHomeBehavior",1);
//                        XposedHelpers.setIntField(param.thisObject,"mLongPressOnHomeBehavior",1);
                        mContext = (Context) XposedHelpers.getObjectField(param.thisObject,"mContext");
                        Log.i(TAG,"mContext==null?:"+(mContext==null));
                        return null;
                    }
                });
        Class < ?> WindowStateClass = XposedHelpers.findClass("android.view.WindowManagerPolicy$WindowState",lpparam.classLoader);
        XposedHelpers.findAndHookMethod("com.android.internal.policy.impl.PhoneWindowManager",
                lpparam.classLoader, "interceptKeyBeforeDispatching",
                WindowStateClass, KeyEvent.class, int.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        Object win = param.args[0];
                        KeyEvent event = (KeyEvent) param.args[1];
                        final boolean keyguardOn = (boolean)XposedHelpers.callMethod(param.thisObject,"keyguardOn");
                        final int keyCode = event.getKeyCode();
                        final int repeatCount = event.getRepeatCount();
                        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
                        final boolean canceled = event.isCanceled();
                        Handler mHandler = (Handler) XposedHelpers.getObjectField(param.thisObject,"mHandler");

                        if (keyCode == KeyEvent.KEYCODE_HOME) {
                            if (!down) {

                                XposedHelpers.setBooleanField(param.thisObject,"mHomePressed",false);
                                if (XposedHelpers.getBooleanField(param.thisObject,"mHomeConsumed")) {
                                    XposedHelpers.setBooleanField(param.thisObject,"mHomeConsumed",false);
                                    return -1;
                                }

                                if (canceled) {
                                    Log.i(TAG, "Ignoring HOME; event canceled.");
                                    return -1;
                                }

                                if(!homeLongPressedDown){
                                    Log.i(TAG, "launchHomeFromHotKey");
                                    XposedHelpers.callMethod(param.thisObject,"launchHomeFromHotKey");
                                    return -1;
                                } else {
                                    homeLongPressedDown = false;
                                    return -1;
                                }
                            }
                            if (repeatCount != 0 && (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) {
                                if (!keyguardOn) {
                                    Log.i(TAG, "handleLongPressOnHome");
                                    XposedHelpers.callMethod(param.thisObject,"showOrHideRecentAppsDialog",RECENT_APPS_BEHAVIOR_EXIT_TOUCH_MODE_AND_SHOW);
                                    homeLongPressedDown = true;
                                    return -1;
                                } else {
                                    Log.i(TAG, "LongPressOnHome: disable due to keyguardOn");
                                    return -1;
                                }
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_BACK && repeatCount != 0 && (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) {
                            Log.i(TAG, "handleLongPressOnBack");
                            mHandler.postDelayed(longPressBackRunnable, ViewConfiguration.getLongPressTimeout());
                        } else {
                            return XposedBridge.invokeOriginalMethod(param.method,param.thisObject,param.args);
                        }
                        return -1;
                    }
                });
    }


    public static Runnable longPressBackRunnable = new Runnable() {
        public void run() {
                try {
                    if(mContext==null) return;
                    Intent intent = new Intent("android.intent.action.MAIN");
                    PackageManager pm = mContext.getPackageManager();
                    String launcher = "com.android.launcher";
                    intent.addCategory("android.intent.category.HOME");
                    ResolveInfo res = pm.resolveActivity(intent, 0);
                    if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                        launcher = res.activityInfo.packageName;
                    }
                    ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                    for (ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
                        if (info.uid >= 10000 && info.uid <= 19999 && info.importance == 100 && !info.processName.equals(launcher) && !info.processName.equals("com.android.systemui")) {
                            if (info.pkgList == null || info.pkgList.length <= 0) {
//                                Process.killProcess(info.pid);
                                Log.e(TAG,"!!!!!!!!!!Process.killProcess(info.pid);");
                            } else {
                                String[] strArr = info.pkgList;
                                int length = strArr.length;
                                for (int i = 0; i < length; i++) {
                                    XposedHelpers.callMethod(am, "forceStopPackage", new Object[]{strArr[i]});
                                }
                            }
                            Toast.makeText(mContext, String.valueOf((String) pm.getApplicationLabel(pm.getApplicationInfo(info.processName, 0))) + " killed", 0).show();
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG,"kill process error,e="+e);
                }
        }
    };
}