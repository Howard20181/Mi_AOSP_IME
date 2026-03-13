package io.github.howard20181.ime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.util.TypedValue;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.AfterInvocation;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi"})
public class ImeHook extends XposedModule {
    private static final String TAG = "ImeHook";
    private static XposedModule module;
    private static Field sBottomViewHelper = null;
    private static Field mBottomViewHelperImm = null;
    private static Field fieldIsInternationalBuild = null;
    private static Field mInputMethodService = null;
    private static Method methodInputMethodServiceStubGetInstance = null;
    private static String config_navBarLayoutHandle = "";

    public ImeHook(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
        module = this;
    }

    public void onSystemServerLoaded(@NonNull SystemServerLoadedParam param) {
        var classLoader = param.getClassLoader();
        try {
            try {
                hookInputMethodManagerServiceImpl(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook InputMethodManagerServiceImpl", e);
            }
        } catch (Exception e) {
            log(Log.ERROR, TAG, "hook system server", e);
        }
    }

    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        if (!param.isFirstPackage()) return;
        var pn = param.getPackageName();
        var classLoader = param.getClassLoader();
        try {
            try {
                hookInputMethodService(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook InputMethodService", e);
            }
            try {
                hookNavigationBarController(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook NavigationBarController", e);
            }
            try {
                hookNavigationBarInflaterView(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook NavigationBarInflaterView", e);
            }
            try {
                hookNavigationBarView(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook NavigationBarView", e);
            }
            try {
                hookInputMethodBottomManager(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook InputMethodBottomManager", e);
            }
        } catch (Throwable tr) {
            log(Log.ERROR, TAG, "Error hooking " + pn, tr);
        }
    }

    private void hookInputMethodService(ClassLoader classLoader) throws NoSuchMethodException,
            ClassNotFoundException, NoSuchFieldException {
        var classInputMethodService = classLoader.loadClass("android.inputmethodservice.InputMethodService");
        var classInputMethodServiceStub = classLoader.loadClass("android.inputmethodservice.InputMethodServiceStub");
        methodInputMethodServiceStubGetInstance = classInputMethodServiceStub.getDeclaredMethod("getInstance");
        fieldIsInternationalBuild = classInputMethodService.getDeclaredField("IS_INTERNATIONAL_BUILD");
        fieldIsInternationalBuild.setAccessible(true);
        var methodHideImeRenderGesturalNavButtons = classInputMethodService.getDeclaredMethod("hideImeRenderGesturalNavButtons", String.class);
        hook(methodHideImeRenderGesturalNavButtons, HideImeRenderGesturalNavButtonsHooker.class);
    }

    private void hookNavigationBarController(ClassLoader classLoader) throws NoSuchMethodException,
            ClassNotFoundException, NoSuchFieldException {
        var classNavigationBarController$Impl = classLoader.loadClass("android.inputmethodservice.NavigationBarController$Impl");
        mInputMethodService = classNavigationBarController$Impl.getDeclaredField("mService");
        mInputMethodService.setAccessible(true);
        var getImeCaptionBarHeight = classNavigationBarController$Impl.getDeclaredMethod("getImeCaptionBarHeight", boolean.class);
        hook(getImeCaptionBarHeight, GetImeCaptionBarHeightHooker.class);
    }

    private void hookNavigationBarInflaterView(ClassLoader classLoader) throws NoSuchMethodException,
            ClassNotFoundException {
        var classNavigationBarInflaterView = classLoader.loadClass("android.inputmethodservice.navigationbar.NavigationBarInflaterView");
        var methodInflateLayout = classNavigationBarInflaterView.getDeclaredMethod("inflateLayout", String.class);
        var prefs = getRemotePreferences("conf");
        config_navBarLayoutHandle = prefs.getString("nav_bar_layout_handle", "");
        prefs.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            if ("nav_bar_layout_handle".equals(key)) {
                config_navBarLayoutHandle = sharedPreferences.getString(key, "");
            }
        });
        hook(methodInflateLayout, InflateLayoutHooker.class);
    }

    private static class InflateLayoutHooker implements Hooker {

        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            if (callback.getArgs()[0] instanceof String && !config_navBarLayoutHandle.isBlank()) {
                callback.getArgs()[0] = config_navBarLayoutHandle;
            }
        }
    }

    private void hookNavigationBarView(ClassLoader classLoader) {
        try {
            var classDeadZone = classLoader.loadClass("android.inputmethodservice.navigationbar.DeadZone");
            var methodOnConfigurationChanged = classDeadZone.getDeclaredMethod("onConfigurationChanged", int.class);
            hook(methodOnConfigurationChanged, DeadZoneOnConfigurationChangedHooker.class);
        } catch (Exception e) {
            log(Log.ERROR, TAG, "hook DeadZone", e);
        }
    }

    @XposedHooker
    private static class DeadZoneOnConfigurationChangedHooker implements Hooker {
        @AfterInvocation
        public static void after(@NonNull AfterHookCallback callback) {
            try {
                var obj = callback.getThisObject();
                if (obj == null) return;
                var fSizeMin = obj.getClass().getDeclaredField("mSizeMin");
                fSizeMin.setAccessible(true);
                int sizeMin = fSizeMin.getInt(obj);
                var fNavView = obj.getClass().getDeclaredField("mNavigationBarView");
                fNavView.setAccessible(true);
                if (fNavView.get(obj) instanceof android.view.View navView) {
                    navView.setPadding(
                            navView.getPaddingLeft(),
                            navView.getPaddingTop(),
                            navView.getPaddingRight(),
                            sizeMin
                    );
                }

                fSizeMin.setInt(obj, 0);
            } catch (Exception e) {
                module.log(Log.ERROR, TAG, "DeadZoneOnConfigurationChangedHooker", e);
            }
        }
    }

    private static class GetImeCaptionBarHeightHooker implements Hooker {

        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            if (callback.getArgs()[0] instanceof Boolean imeDrawsImeNavBar && imeDrawsImeNavBar) {
                try {
                    var mService = mInputMethodService.get(callback.getThisObject());
                    if (mService instanceof InputMethodService inputMethodService) {
                        var imeCaptionBarHeight = Math.round(TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                48,
                                inputMethodService.getResources().getDisplayMetrics()
                        ));
                        callback.returnAndSkip(imeCaptionBarHeight);
                    }
                } catch (IllegalAccessException e) {
                    module.log(Log.ERROR, TAG, "GetImeCaptionBarHeightHooker", e);
                }
            }
        }
    }

    private static class HideImeRenderGesturalNavButtonsHooker implements Hooker {
        private static boolean originalIsInternationalBuild;

        @BeforeInvocation
        public static boolean before(@NonNull BeforeHookCallback callback) {
            if (fieldIsInternationalBuild != null) {
                try {
                    originalIsInternationalBuild = fieldIsInternationalBuild.getBoolean(callback.getThisObject());
                    var InputMethodServiceInjector = module.invokeOrigin(methodInputMethodServiceStubGetInstance, callback.getThisObject());
                    if (InputMethodServiceInjector != null) {
                        var methodIsImeSupport = InputMethodServiceInjector.getClass().getDeclaredMethod("isImeSupport", Context.class);
                        methodIsImeSupport.setAccessible(true);
                        if (callback.getThisObject() instanceof InputMethodService inputMethodService) {
                            if (module.invokeOrigin(methodIsImeSupport, InputMethodServiceInjector,
                                    inputMethodService.getApplicationContext()) instanceof Boolean isImeSupport && !isImeSupport) {
                                fieldIsInternationalBuild.setBoolean(callback.getThisObject(), true);
                            }
                        }
                    }
                } catch (IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException e) {
                    module.log(Log.ERROR, TAG, "HideImeRenderGesturalNavButtonsHooker", e);
                }
            }
            return originalIsInternationalBuild;
        }

        @AfterInvocation
        public static void after(@NonNull AfterHookCallback callback, boolean originalIsInternationalBuild) {
            if (fieldIsInternationalBuild != null) {
                try {
                    fieldIsInternationalBuild.setBoolean(callback.getThisObject(), originalIsInternationalBuild);
                } catch (IllegalAccessException e) {
                    module.log(Log.ERROR, TAG, "HideImeRenderGesturalNavButtonsHooker", e);
                }
            }
        }
    }

    private void hookInputMethodManagerServiceImpl(ClassLoader classLoader)
            throws NoSuchMethodException, ClassNotFoundException {
        var classInputMethodManagerServiceImpl = classLoader.loadClass("com.android.server.inputmethod.InputMethodManagerServiceImpl");
        var methodIsCallingBetweenCustomIME = classInputMethodManagerServiceImpl.getDeclaredMethod("isCallingBetweenCustomIME", Context.class, int.class, String.class);
        hook(methodIsCallingBetweenCustomIME, IsCallingBetweenCustomIMEHooker.class);
    }

    private void hookInputMethodBottomManager(ClassLoader classLoader) throws NoSuchMethodException,
            ClassNotFoundException {
        var classInputMethodModuleManager = classLoader.loadClass("android.inputmethodservice.InputMethodModuleManager");
        var methodLoadDex = classInputMethodModuleManager.getDeclaredMethod("loadDex",
                ClassLoader.class, String.class);
        hook(methodLoadDex, GetClassloaderHooker.class);
    }

    @XposedHooker
    private static class IsCallingBetweenCustomIMEHooker implements Hooker {

        @AfterInvocation
        public static void after(@NonNull AfterHookCallback callback) {
            var args = callback.getArgs();
            if (callback.getResult() instanceof Boolean isCallingBetweenCustomIME
                    && !isCallingBetweenCustomIME && args.length >= 3
                    && args[0] instanceof Context context && args[1] instanceof Integer uid) {
                var imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                var currentInputMethodInfo = imm.getCurrentInputMethodInfo();
                if (currentInputMethodInfo != null) {
                    var currentInputMethodInfoPackageName = currentInputMethodInfo.getPackageName();
                    var packagesForQueryingUid = context.getPackageManager().getPackagesForUid(uid);
                    if (packagesForQueryingUid != null) {
                        for (var queryingUidPackageName : packagesForQueryingUid) {
                            if (currentInputMethodInfoPackageName.equals(queryingUidPackageName)) {
                                callback.setResult(true);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    @XposedHooker
    private static class GetClassloaderHooker implements Hooker {

        @AfterInvocation
        public static void after(@NonNull AfterHookCallback callback) {
            try {
                var args = callback.getArgs();
                if (args.length >= 1 && args[0] instanceof ClassLoader imeModuleClassLoader) {
                    var classInputMethodBottomManager = imeModuleClassLoader.loadClass("com.miui.inputmethod.InputMethodBottomManager");
                    var methodGetSupportIme = classInputMethodBottomManager.getDeclaredMethod("getSupportIme");
                    var classBottomViewHelper = imeModuleClassLoader.loadClass("com.miui.inputmethod.InputMethodBottomManager$BottomViewHelper");
                    mBottomViewHelperImm = classBottomViewHelper.getDeclaredField("mImm");
                    mBottomViewHelperImm.setAccessible(true);
                    sBottomViewHelper = classInputMethodBottomManager.getDeclaredField("sBottomViewHelper");
                    sBottomViewHelper.setAccessible(true);
                    module.hook(methodGetSupportIme, GetSupportImeHooker.class);
                }
            } catch (NoSuchFieldException | ClassNotFoundException |
                     NoSuchMethodException e) {
                module.log(Log.ERROR, TAG, "GetClassloaderHooker", e);
            }
        }
    }

    @XposedHooker
    private static class GetSupportImeHooker implements Hooker {

        @BeforeInvocation
        public static void before(@NonNull BeforeHookCallback callback) {
            try {
                var instanceBottomViewHelper = sBottomViewHelper.get(callback.getThisObject());
                if (instanceBottomViewHelper != null && mBottomViewHelperImm != null) {
                    if (mBottomViewHelperImm.get(instanceBottomViewHelper) instanceof InputMethodManager inputMethodManager) {
                        var enabledInputMethodList = inputMethodManager.getEnabledInputMethodList();
                        callback.returnAndSkip(enabledInputMethodList);
                    }
                }
            } catch (IllegalAccessException e) {
                module.log(Log.ERROR, TAG, "GetSupportImeHooker", e);
            }
        }
    }
}
