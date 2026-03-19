package io.github.howard20181.ime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.om.OverlayManager;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.RoundedCorner;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import bridge.HiddenApiBridge;
import io.github.libxposed.api.XposedModule;

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi"})
public class ImeHook extends XposedModule {
    private static final String TAG = "ImeHook";
    private static XposedModule module;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        module = this;
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        var classLoader = param.getClassLoader();
        try {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    hookInputMethodManagerService(classLoader);
                } catch (Exception e) {
                    log(Log.ERROR, TAG, "hook InputMethodManagerService", e);
                }
            }
            try {
                hookInputMethodManagerServiceImpl(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook InputMethodManagerServiceImpl", e);
            }
        } catch (Exception e) {
            log(Log.ERROR, TAG, "hook system server", e);
        }
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
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
            ClassNotFoundException {
        var classInputMethodService = classLoader.loadClass("android.inputmethodservice.InputMethodService");
        try {
            var fieldIsInternationalBuild = classInputMethodService.getDeclaredField("IS_INTERNATIONAL_BUILD");
            fieldIsInternationalBuild.setAccessible(true);
            var methodHideImeRenderGesturalNavButtons = classInputMethodService.getDeclaredMethod("hideImeRenderGesturalNavButtons", String.class);
            hook(methodHideImeRenderGesturalNavButtons).intercept(chain -> {
                try {
                    var classInputMethodServiceStub = classLoader.loadClass("android.inputmethodservice.InputMethodServiceStub");
                    var methodInputMethodServiceStubGetInstance = classInputMethodServiceStub.getDeclaredMethod("getInstance");
                    var InputMethodServiceInjector = getInvoker(methodInputMethodServiceStubGetInstance).invoke(chain.getThisObject());
                    if (InputMethodServiceInjector != null) {
                        var methodIsImeSupport = InputMethodServiceInjector.getClass().getDeclaredMethod("isImeSupport", Context.class);
                        methodIsImeSupport.setAccessible(true);
                        if (chain.getThisObject() instanceof InputMethodService inputMethodService) {
                            if (getInvoker(methodIsImeSupport).invoke(InputMethodServiceInjector,
                                    inputMethodService.getApplicationContext()) instanceof Boolean isImeSupport && !isImeSupport) {
                                fieldIsInternationalBuild.setBoolean(chain.getThisObject(), true);
                            }
                        }
                    }
                } catch (IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException | ClassNotFoundException e) {
                    log(Log.ERROR, TAG, "hook hideImeRenderGesturalNavButtons", e);
                }
                return chain.proceed();
            });
        } catch (NoSuchFieldException e) {
            log(Log.WARN, TAG, "IS_INTERNATIONAL_BUILD not found", e);
        }
    }

    private void hookNavigationBarController(ClassLoader classLoader) throws
            ClassNotFoundException, NoSuchFieldException {
        var classNavigationBarController$Impl = classLoader.loadClass("android.inputmethodservice.NavigationBarController$Impl");
        var mImeDrawsImeNavBar = classNavigationBarController$Impl.getDeclaredField("mImeDrawsImeNavBar");
        mImeDrawsImeNavBar.setAccessible(true);
        var mInputMethodService = classNavigationBarController$Impl.getDeclaredField("mService");
        mInputMethodService.setAccessible(true);
        try {
            Method getImeCaptionBarHeight;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                getImeCaptionBarHeight = classNavigationBarController$Impl.getDeclaredMethod("getImeCaptionBarHeight", boolean.class);
            } else {
                getImeCaptionBarHeight = classNavigationBarController$Impl.getDeclaredMethod("getImeCaptionBarHeight");
            }
            hook(getImeCaptionBarHeight).intercept(chain -> {
                try {
                    boolean imeShouldShowImeNavBar;
                    if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof Boolean imeDrawsImeNavBar) {
                        imeShouldShowImeNavBar = imeDrawsImeNavBar;
                    } else {
                        imeShouldShowImeNavBar = mImeDrawsImeNavBar.getBoolean(chain.getThisObject());
                    }
                    if (imeShouldShowImeNavBar) {
                        var mService = mInputMethodService.get(chain.getThisObject());
                        if (mService instanceof InputMethodService inputMethodService) {
                            return dpToPx(48, inputMethodService.getResources());
                        }
                    }
                } catch (IllegalAccessException e) {
                    log(Log.ERROR, TAG, "hook getImeCaptionBarHeight", e);
                }
                return chain.proceed();
            });
        } catch (NoSuchMethodException e) {
            log(Log.WARN, TAG, "getImeCaptionBarHeight method not found", e);
        }
    }

    private void hookNavigationBarInflaterView(ClassLoader classLoader) throws NoSuchMethodException,
            ClassNotFoundException {
        var classNavigationBarInflaterView = classLoader.loadClass("android.inputmethodservice.navigationbar.NavigationBarInflaterView");
        var methodInflateLayout = classNavigationBarInflaterView.getDeclaredMethod("inflateLayout", String.class);
        var prefs = getRemotePreferences("conf");
        AtomicReference<String> config_navBarLayoutHandle = new AtomicReference<>(prefs.getString("nav_bar_layout_handle", ""));
        prefs.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
            if ("nav_bar_layout_handle".equals(key)) {
                config_navBarLayoutHandle.set(sharedPreferences.getString(key, ""));
                module.log(Log.INFO, TAG, "nav_bar_layout_handle changed to " + config_navBarLayoutHandle.get());
            }
        });
        hook(methodInflateLayout).intercept(chain -> {
            if (chain.getArgs().get(0) instanceof String && !config_navBarLayoutHandle.get().isBlank()) {
                var args = chain.getArgs().toArray();
                args[0] = config_navBarLayoutHandle.get();
                module.log(Log.INFO, TAG, "Inflating layout: " + args[0]);
                return chain.proceed(args);
            }
            return chain.proceed();
        });
    }

    private static final WeakHashMap<View, int[]> BASE_PADDINGS = new WeakHashMap<>();

    private void hookNavigationBarView(ClassLoader classLoader) throws NoSuchMethodException,
            ClassNotFoundException, NoSuchFieldException {
        var classNavigationBarView = classLoader.loadClass("android.inputmethodservice.navigationbar.NavigationBarView");
        var updateOrientationViews = classNavigationBarView.getDeclaredMethod("updateOrientationViews");
        var fHorizontal = classNavigationBarView.getDeclaredField("mHorizontal");
        fHorizontal.setAccessible(true);
        hook(updateOrientationViews).intercept(chain -> {
            var result = chain.proceed();
            var obj = chain.getThisObject();
            if (obj == null) return result;
            try {
                if (fHorizontal.get(obj) instanceof View horizontalView) {
                    var shadow = dpToPx(4, horizontalView.getResources());
                    horizontalView.setOnApplyWindowInsetsListener((v, insets) -> {
                        var basePadding = BASE_PADDINGS.computeIfAbsent(v, x -> new int[]{
                                x.getPaddingLeft() + shadow,
                                x.getPaddingTop(),
                                x.getPaddingRight() + shadow,
                                x.getPaddingBottom()
                        });
                        var bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
                        var bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
                        int radiusBottomLeft = bottomLeft != null ? bottomLeft.getRadius() : 0;
                        int radiusBottomRight = bottomRight != null ? bottomRight.getRadius() : 0;
                        v.setPadding(radiusBottomLeft > 0 ? radiusBottomLeft - basePadding[0] : basePadding[0],
                                basePadding[1],
                                radiusBottomRight > 0 ? radiusBottomRight - basePadding[2] : basePadding[2],
                                basePadding[3]);
                        return insets;
                    });
                }
            } catch (IllegalAccessException e) {
                log(Log.ERROR, TAG, "hook updateOrientationViews", e);
            }
            return result;
        });
    }

    private void hookDeadZone(ClassLoader classLoader) {
        try {
            var classDeadZone = classLoader.loadClass("android.inputmethodservice.navigationbar.DeadZone");
            var fSizeMin = classDeadZone.getDeclaredField("mSizeMin");
            var methodOnConfigurationChanged = classDeadZone.getDeclaredMethod("onConfigurationChanged", int.class);
            hook(methodOnConfigurationChanged).intercept(chain -> {
                var result = chain.proceed();
                try {
                    var obj = chain.getThisObject();
                    if (obj == null) return result;
                    fSizeMin.setAccessible(true);
                    fSizeMin.setInt(obj, 0);
                } catch (Exception e) {
                    log(Log.ERROR, TAG, "hook DeadZone.onConfigurationChanged", e);
                }
                return result;
            });
        } catch (Exception e) {
            log(Log.ERROR, TAG, "hook DeadZone", e);
        }
    }

    private static int dpToPx(int data, Resources res) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, data, res.getDisplayMetrics()));
    }

    private void hookInputMethodManagerService(ClassLoader classLoader)
            throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        var classInputMethodManagerService = classLoader.loadClass("com.android.server.inputmethod.InputMethodManagerService");
        var methodGetInputMethodNavButtonFlagsLocked = classInputMethodManagerService.getDeclaredMethod("getInputMethodNavButtonFlagsLocked");
        var fieldImeDrawsImeNavBarRes = classInputMethodManagerService.getDeclaredField("mImeDrawsImeNavBarRes");
        fieldImeDrawsImeNavBarRes.setAccessible(true);
        var classOverlayableSystemBooleanResourceWrapper = classLoader.loadClass("com.android.server.inputmethod.OverlayableSystemBooleanResourceWrapper");
        var fieldValueRefOverlayableSystemBooleanResourceWrapper = classOverlayableSystemBooleanResourceWrapper.getDeclaredField("mValueRef");
        fieldValueRefOverlayableSystemBooleanResourceWrapper.setAccessible(true);
        var fieldSettings = classInputMethodManagerService.getDeclaredField("mSettings");
        fieldSettings.setAccessible(true);
        var fContext = classInputMethodManagerService.getDeclaredField("mContext");
        fContext.setAccessible(true);
        var InputMethodManagerServiceStubClass = classLoader.loadClass("com.android.server.inputmethod.InputMethodManagerServiceStub");
        var methodInputMethodManagerServiceStubGetInstance = InputMethodManagerServiceStubClass.getDeclaredMethod("getInstance");
        final String NAV_BAR_MODE_GESTURAL_OVERLAY = "com.android.internal.systemui.navbar.gestural";
        hook(methodGetInputMethodNavButtonFlagsLocked).intercept(chain -> {
            try {
                var mImeDrawsImeNavBarRes = fieldImeDrawsImeNavBarRes.get(chain.getThisObject());
                Object valueRef = fieldValueRefOverlayableSystemBooleanResourceWrapper.get(mImeDrawsImeNavBarRes);
                if (!(valueRef instanceof AtomicBoolean)) {
                    log(Log.WARN, TAG, "mValueRef is not an AtomicBoolean; skipping nav bar flag adjustment");
                    return chain.proceed();
                }
                var mImeDrawsImeNavBar = (AtomicBoolean) valueRef;
                if (mImeDrawsImeNavBar != null) {
                    var InputMethodManagerServiceImpl = getInvoker(methodInputMethodManagerServiceStubGetInstance).invoke(chain.getThisObject());
                    if (InputMethodManagerServiceImpl != null) {
                        var methodIsCustomizedInputMethod = InputMethodManagerServiceImpl.getClass().getDeclaredMethod("isCustomizedInputMethod", String.class);
                        methodIsCustomizedInputMethod.setAccessible(true);
                        var mSettings = fieldSettings.get(chain.getThisObject());
                        if (mSettings != null && fContext.get(chain.getThisObject()) instanceof Context mContext) {
                            var getSelectedInputMethod = mSettings.getClass().getDeclaredMethod("getSelectedInputMethod");
                            getSelectedInputMethod.setAccessible(true);
                            var isGesturesNav = Settings.Secure.getInt(mContext.getContentResolver(), "navigation_mode", 2) == 2;
                            var canImeDrawsImeNavBar = isGesturesNav && getInvoker(methodIsCustomizedInputMethod).invoke(InputMethodManagerServiceImpl,
                                    getSelectedInputMethod.invoke(mSettings)) instanceof Boolean isCustomizedInputMethod && !isCustomizedInputMethod;
                            try {
                                if (mContext.getSystemService(Context.OVERLAY_SERVICE) instanceof OverlayManager overlayManager) {
                                    var overlayInfo = HiddenApiBridge.OverlayManager_getOverlayInfo(overlayManager, NAV_BAR_MODE_GESTURAL_OVERLAY, HiddenApiBridge.UserHandle_CURRENT());
                                    if (overlayInfo != null) {
                                        var enabled = HiddenApiBridge.OverlayInfo_isEnabled(overlayInfo);
                                        if (enabled != canImeDrawsImeNavBar) {
                                            HiddenApiBridge.OverlayManager_setEnabled(overlayManager, NAV_BAR_MODE_GESTURAL_OVERLAY, canImeDrawsImeNavBar, HiddenApiBridge.UserHandle_CURRENT());
                                        }
                                    }
                                }
                            } catch (SecurityException | IllegalStateException e) {
                                log(Log.ERROR, TAG, "Failed to toggle gestural nav overlay", e);
                            }
                            mImeDrawsImeNavBar.set(canImeDrawsImeNavBar);
                        }
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                log(Log.ERROR, TAG, "hook getInputMethodNavButtonFlagsLocked", e);
            }
            return chain.proceed();
        });
    }

    private void hookInputMethodManagerServiceImpl(ClassLoader classLoader)
            throws NoSuchMethodException, ClassNotFoundException {
        var classInputMethodManagerServiceImpl = classLoader.loadClass("com.android.server.inputmethod.InputMethodManagerServiceImpl");
        var methodIsCallingBetweenCustomIME = classInputMethodManagerServiceImpl.getDeclaredMethod("isCallingBetweenCustomIME", Context.class, int.class, String.class);
        hook(methodIsCallingBetweenCustomIME).intercept(chain -> {
            var args = chain.getArgs();
            var result = chain.proceed();
            if (result instanceof Boolean isCallingBetweenCustomIME
                    && !isCallingBetweenCustomIME && args.size() >= 3
                    && args.get(0) instanceof Context context && args.get(1) instanceof Integer uid) {
                var imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                var currentInputMethodInfo = imm.getCurrentInputMethodInfo();
                if (currentInputMethodInfo != null) {
                    var currentInputMethodInfoPackageName = currentInputMethodInfo.getPackageName();
                    var packagesForQueryingUid = context.getPackageManager().getPackagesForUid(uid);
                    if (packagesForQueryingUid != null) {
                        for (var queryingUidPackageName : packagesForQueryingUid) {
                            if (currentInputMethodInfoPackageName.equals(queryingUidPackageName)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return result;
        });
    }

    private void hookInputMethodBottomManager(ClassLoader classLoader) throws
            NoSuchMethodException,
            ClassNotFoundException {
        var classInputMethodModuleManager = classLoader.loadClass("android.inputmethodservice.InputMethodModuleManager");
        var methodLoadDex = classInputMethodModuleManager.getDeclaredMethod("loadDex",
                ClassLoader.class, String.class);
        hook(methodLoadDex).intercept(chain -> {
            var result = chain.proceed();
            try {
                var args = chain.getArgs();
                if (!args.isEmpty() && args.get(0) instanceof ClassLoader imeModuleClassLoader) {
                    var classInputMethodBottomManager = imeModuleClassLoader.loadClass("com.miui.inputmethod.InputMethodBottomManager");
                    var methodGetSupportIme = classInputMethodBottomManager.getDeclaredMethod("getSupportIme");
                    var classBottomViewHelper = imeModuleClassLoader.loadClass("com.miui.inputmethod.InputMethodBottomManager$BottomViewHelper");
                    var mBottomViewHelperImm = classBottomViewHelper.getDeclaredField("mImm");
                    mBottomViewHelperImm.setAccessible(true);
                    var sBottomViewHelper = classInputMethodBottomManager.getDeclaredField("sBottomViewHelper");
                    sBottomViewHelper.setAccessible(true);
                    module.hook(methodGetSupportIme).intercept(chain1 -> {
                        try {
                            var instanceBottomViewHelper = sBottomViewHelper.get(chain1.getThisObject());
                            if (instanceBottomViewHelper != null) {
                                if (mBottomViewHelperImm.get(instanceBottomViewHelper) instanceof InputMethodManager inputMethodManager) {
                                    return inputMethodManager.getEnabledInputMethodList();
                                }
                            }
                        } catch (IllegalAccessException e) {
                            log(Log.ERROR, TAG, "hook getSupportIme", e);
                        }
                        return chain1.proceed();
                    });
                }
            } catch (NoSuchFieldException | ClassNotFoundException |
                     NoSuchMethodException e) {
                log(Log.ERROR, TAG, "hook loadDex", e);
            }
            return result;
        });
    }
}
