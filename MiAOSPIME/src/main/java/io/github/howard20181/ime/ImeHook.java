package io.github.howard20181.ime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

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
                hookDeadZone(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook DeadZone", e);
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

    private void hookNavigationBarView(ClassLoader classLoader) throws NoSuchMethodException,
            ClassNotFoundException {
        var classNavigationBarView = classLoader.loadClass("android.inputmethodservice.navigationbar.NavigationBarView");
        var updateOrientationViews = classNavigationBarView.getDeclaredMethod("updateOrientationViews");
        hook(updateOrientationViews, NavigationBarViewUpdateOrientationViewsHooker.class);
    }

    private void hookDeadZone(ClassLoader classLoader) {
        try {
            var classDeadZone = classLoader.loadClass("android.inputmethodservice.navigationbar.DeadZone");
            var methodOnConfigurationChanged = classDeadZone.getDeclaredMethod("onConfigurationChanged", int.class);
            hook(methodOnConfigurationChanged, DeadZoneOnConfigurationChangedHooker.class);
        } catch (Exception e) {
            log(Log.ERROR, TAG, "hook DeadZone", e);
        }
    }

    @XposedHooker
    private static class NavigationBarViewUpdateOrientationViewsHooker implements Hooker {
        private static final WeakHashMap<View, int[]> BASE_PADDINGS = new WeakHashMap<>();
        private static final WeakHashMap<View, int[]> ROUNDED_CORNER_STATE = new WeakHashMap<>();
        private static final int ZERO_ROUNDED_GRACE_CALLBACKS = 1;

        @AfterInvocation
        public static void after(@NonNull AfterHookCallback callback) {
            var obj = callback.getThisObject();
            if (obj == null) return;
            try {
                var fHorizontal = obj.getClass().getDeclaredField("mHorizontal");
                fHorizontal.setAccessible(true);
                var horizontalObj = fHorizontal.get(obj);
                if (!(horizontalObj instanceof View horizontalView)) return;
                horizontalView.setOnApplyWindowInsetsListener((v, insets) -> {
                    var basePadding = BASE_PADDINGS.computeIfAbsent(v, x -> new int[]{
                            x.getPaddingLeft(),
                            x.getPaddingTop(),
                            x.getPaddingRight(),
                            x.getPaddingBottom()
                    });
                    var roundedState = ROUNDED_CORNER_STATE.computeIfAbsent(v, x -> new int[]{0, 0, 0});

                    var cutout = insets.getDisplayCutout();
                    int safeLeft = 0;
                    int safeRight = 0;
                    int waterfallLeft = 0;
                    int waterfallRight = 0;
                    if (cutout != null) {
                        safeLeft = cutout.getSafeInsetLeft();
                        safeRight = cutout.getSafeInsetRight();
                        var waterfall = cutout.getWaterfallInsets();
                        waterfallLeft = waterfall.left;
                        waterfallRight = waterfall.right;
                    }

                    var bottomLeft = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_LEFT);
                    var bottomRight = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT);
                    int bottomLeftRadius = bottomLeft != null ? bottomLeft.getRadius() : 0;
                    int bottomRightRadius = bottomRight != null ? bottomRight.getRadius() : 0;

                    int roundedLeftUsed = bottomLeftRadius;
                    int roundedRightUsed = bottomRightRadius;
                    if (bottomLeftRadius == 0 && bottomRightRadius == 0) {
                        roundedState[2] += 1;
                        if (roundedState[2] <= ZERO_ROUNDED_GRACE_CALLBACKS) {
                            roundedLeftUsed = roundedState[0];
                            roundedRightUsed = roundedState[1];
                        } else {
                            roundedState[0] = 0;
                            roundedState[1] = 0;
                        }
                    } else {
                        roundedState[0] = bottomLeftRadius;
                        roundedState[1] = bottomRightRadius;
                        roundedState[2] = 0;
                    }

                    int candidateLeft = Math.max(Math.max(safeLeft, waterfallLeft), roundedLeftUsed);
                    int candidateRight = Math.max(Math.max(safeRight, waterfallRight), roundedRightUsed);

                    int appliedLeft = basePadding[0] + candidateLeft;
                    int appliedRight = basePadding[2] + candidateRight;
                    v.setPadding(appliedLeft, basePadding[1], appliedRight, basePadding[3]);

                    return insets;
                });
            } catch (NoSuchFieldException | IllegalAccessException e) {
                module.log(Log.ERROR, TAG, "NavigationBarViewUpdateOrientationViewsHooker", e);
            }
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
