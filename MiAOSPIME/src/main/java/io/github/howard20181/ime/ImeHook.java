package io.github.howard20181.ime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.om.OverlayManager;
import android.content.res.Resources;
import android.graphics.Insets;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.RoundedCorner;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import bridge.HiddenApiBridge;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

@SuppressLint({"PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi"})
public class ImeHook extends XposedModule {
    private static final String TAG = "ImeHook";
    private static final String HOOK_HIDE_GESTURAL_BUTTONS = "ime_hide_gestural_buttons";
    private static final String HOOK_CAPTION_BAR_HEIGHT = "ime_caption_bar_height";
    private static final String HOOK_CAPTION_BAR_INSETS_HEIGHT =
            "ime_caption_bar_insets_height";
    private static final String HOOK_SYSTEM_INSETS_HEIGHT = "ime_system_insets_height";
    private static final String HOOK_INPUT_VIEW_BOTTOM_INSET = "ime_input_view_bottom_inset";
    private static final String HOOK_DISPATCH_INPUT_VIEW_INSETS =
            "ime_dispatch_input_view_insets";
    private static final String HOOK_INFLATE_NAV_LAYOUT = "ime_inflate_nav_layout";
    private static final String HOOK_UPDATE_ORIENTATION = "ime_update_orientation";
    private static final String HOOK_DEAD_ZONE = "ime_dead_zone";
    private static final String HOOK_NAV_BUTTON_FLAGS = "server_ime_nav_button_flags";
    private static final String HOOK_CUSTOM_IME_CALLER = "server_custom_ime_caller";
    private static final String HOOK_LOAD_BOTTOM_DEX = "ime_load_bottom_dex";
    private static final String HOOK_SUPPORTED_IME_LIST = "ime_supported_ime_list";

    private final List<XposedInterface.HookHandle> hookHandles = new ArrayList<>();
    private final AtomicReference<String> navBarLayoutHandle = new AtomicReference<>("");
    private SharedPreferences navBarPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener navBarPreferenceListener;

    @Override
    public boolean onHotReloading(XposedModuleInterface.HotReloadingParam param) {
        releaseNavBarLayoutPreferences();
        log(Log.INFO, TAG, "Hot reloading with " + hookHandles.size() + " hooks");
        return true;
    }

    @Override
    public void onHotReloaded(XposedModuleInterface.HotReloadedParam param) {
        boolean needsNavBarPreferences = false;
        int replaced = 0;
        ClassLoader targetClassLoader = null;
        Set<String> installedHookIds = new HashSet<>();
        for (XposedInterface.HookHandle oldHandle : param.getOldHookHandles()) {
            try {
                if (targetClassLoader == null && oldHandle.getExecutable() != null) {
                    targetClassLoader = oldHandle.getExecutable().getDeclaringClass().getClassLoader();
                }
                String hookId = oldHandle.getId();
                if (hookId == null || hookId.isBlank()) {
                    hookId = identifyHook(oldHandle.getExecutable());
                }
                XposedInterface.Hooker replacement = createHotReloadHooker(hookId);
                if (replacement == null) {
                    oldHandle.unhook();
                    continue;
                }
                recordHookHandle(oldHandle.replaceHook(replacement));
                replaced++;
                installedHookIds.add(hookId);
                needsNavBarPreferences |= HOOK_INFLATE_NAV_LAYOUT.equals(hookId);
            } catch (Throwable throwable) {
                log(Log.ERROR, TAG, "replace hook while hot reloading", throwable);
            }
        }
        if (needsNavBarPreferences) {
            initializeNavBarLayoutPreferences();
        }
        backfillHotReloadHooks(param, targetClassLoader, installedHookIds);
        log(Log.INFO, TAG, "Hot reload completed with " + replaced + " hooks");
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        var classLoader = param.getClassLoader();
        try {
            try {
                hookInputMethodManagerService(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook InputMethodManagerService", e);
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
                hookInputViewBottomInset(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook InputMethodService.setInputView", e);
            }
            try {
                hookInputViewInsetsDispatch(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook View.dispatchApplyWindowInsets", e);
            }
            try {
                hookNavigationBarController(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook NavigationBarController", e);
            }
            try {
                hookCaptionBarInsetsHeight(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook InsetsController.setImeCaptionBarInsetsHeight", e);
            }
            try {
                hookNavigationBarSystemInsets(classLoader);
            } catch (Exception e) {
                log(Log.ERROR, TAG, "hook NavigationBarController.getSystemInsets", e);
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
            classInputMethodService.getDeclaredField("IS_INTERNATIONAL_BUILD");
            var methodHideImeRenderGesturalNavButtons = classInputMethodService.getDeclaredMethod("hideImeRenderGesturalNavButtons", String.class);
            recordHookHandle(hook(methodHideImeRenderGesturalNavButtons)
                    .setId(HOOK_HIDE_GESTURAL_BUTTONS)
                    .intercept(this::interceptHideGesturalButtons));
        } catch (NoSuchFieldException e) {
            log(Log.WARN, TAG, "IS_INTERNATIONAL_BUILD not found", e);
        }
    }

    private void hookNavigationBarController(ClassLoader classLoader) throws
            ClassNotFoundException {
        Class<?> classNavigationBarController$Impl = loadNavigationBarControllerClass(classLoader);
        try {
            Method getImeCaptionBarHeight;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                getImeCaptionBarHeight = classNavigationBarController$Impl.getDeclaredMethod("getImeCaptionBarHeight", boolean.class);
            } else {
                getImeCaptionBarHeight = classNavigationBarController$Impl.getDeclaredMethod("getImeCaptionBarHeight");
            }
            recordHookHandle(hook(getImeCaptionBarHeight)
                    .setId(HOOK_CAPTION_BAR_HEIGHT)
                    .intercept(this::interceptCaptionBarHeight));
        } catch (NoSuchMethodException e) {
            log(Log.WARN, TAG, "getImeCaptionBarHeight method not found", e);
        }
    }

    private void hookInputViewBottomInset(ClassLoader classLoader) throws Exception {
        if (!usesStandaloneNavigationBarController(classLoader)) return;
        Class<?> inputMethodService = classLoader.loadClass(
                "android.inputmethodservice.InputMethodService");
        Method setInputView = inputMethodService.getDeclaredMethod("setInputView", View.class);
        recordHookHandle(hook(setInputView).setId(HOOK_INPUT_VIEW_BOTTOM_INSET)
                .intercept(this::interceptInputViewBottomInset));
    }

    private void hookCaptionBarInsetsHeight(ClassLoader classLoader) throws Exception {
        if (!usesStandaloneNavigationBarController(classLoader)) return;
        Class<?> insetsController = classLoader.loadClass("android.view.InsetsController");
        Method setter = insetsController.getDeclaredMethod("setImeCaptionBarInsetsHeight",
                int.class);
        recordHookHandle(hook(setter).setId(HOOK_CAPTION_BAR_INSETS_HEIGHT)
                .intercept(this::interceptCaptionBarInsetsHeight));
    }

    private void hookInputViewInsetsDispatch(ClassLoader classLoader) throws Exception {
        if (!usesStandaloneNavigationBarController(classLoader)) return;
        Class<?> viewClass = classLoader.loadClass("android.view.View");
        Method dispatchInsets = viewClass.getDeclaredMethod("dispatchApplyWindowInsets",
                WindowInsets.class);
        recordHookHandle(hook(dispatchInsets).setId(HOOK_DISPATCH_INPUT_VIEW_INSETS)
                .intercept(this::interceptInputViewInsetsDispatch));
    }

    private void hookNavigationBarSystemInsets(ClassLoader classLoader) throws Exception {
        Class<?> controllerClass = loadNavigationBarControllerClass(classLoader);
        if (!"android.inputmethodservice.NavigationBarController".equals(
                controllerClass.getName())) {
            return;
        }
        Method getSystemInsets = controllerClass.getDeclaredMethod("getSystemInsets");
        recordHookHandle(hook(getSystemInsets).setId(HOOK_SYSTEM_INSETS_HEIGHT)
                .intercept(this::interceptNavigationBarSystemInsets));
    }

    private Class<?> loadNavigationBarControllerClass(ClassLoader classLoader)
            throws ClassNotFoundException {
        try {
            return classLoader.loadClass("android.inputmethodservice.NavigationBarController$Impl");
        } catch (ClassNotFoundException ignored) {
            return classLoader.loadClass("android.inputmethodservice.NavigationBarController");
        }
    }

    private boolean usesStandaloneNavigationBarController(ClassLoader classLoader)
            throws ClassNotFoundException {
        return "android.inputmethodservice.NavigationBarController".equals(
                loadNavigationBarControllerClass(classLoader).getName());
    }

    private void hookNavigationBarInflaterView(ClassLoader classLoader) throws NoSuchMethodException,
            ClassNotFoundException {
        var classNavigationBarInflaterView = classLoader.loadClass("android.inputmethodservice.navigationbar.NavigationBarInflaterView");
        var methodInflateLayout = classNavigationBarInflaterView.getDeclaredMethod("inflateLayout", String.class);
        initializeNavBarLayoutPreferences();
        recordHookHandle(hook(methodInflateLayout).setId(HOOK_INFLATE_NAV_LAYOUT)
                .intercept(this::interceptInflateNavLayout));
    }

    private static final WeakHashMap<View, int[]> BASE_PADDINGS = new WeakHashMap<>();
    private static final WeakHashMap<View, String> ACTIVE_NAV_BAR_LAYOUTS =
            new WeakHashMap<>();

    private void hookNavigationBarView(ClassLoader classLoader) throws NoSuchMethodException,
            ClassNotFoundException {
        var classNavigationBarView = classLoader.loadClass("android.inputmethodservice.navigationbar.NavigationBarView");
        var updateOrientationViews = classNavigationBarView.getDeclaredMethod("updateOrientationViews");
        recordHookHandle(hook(updateOrientationViews).setId(HOOK_UPDATE_ORIENTATION)
                .intercept(this::interceptUpdateOrientation));
    }

    private void hookDeadZone(ClassLoader classLoader) {
        try {
            var classDeadZone = classLoader.loadClass("android.inputmethodservice.navigationbar.DeadZone");
            var methodOnConfigurationChanged = classDeadZone.getDeclaredMethod("onConfigurationChanged", int.class);
            recordHookHandle(hook(methodOnConfigurationChanged).setId(HOOK_DEAD_ZONE)
                    .intercept(this::interceptDeadZone));
        } catch (Exception e) {
            log(Log.ERROR, TAG, "hook DeadZone", e);
        }
    }

    private static int dpToPx(int data, Resources res) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, data, res.getDisplayMetrics()));
    }

    private void hookInputMethodManagerService(ClassLoader classLoader)
            throws NoSuchMethodException, ClassNotFoundException {
        var classInputMethodManagerService = classLoader.loadClass("com.android.server.inputmethod.InputMethodManagerService");
        Method methodGetInputMethodNavButtonFlagsLocked = null;
        for (Method method : classInputMethodManagerService.getDeclaredMethods()) {
            if ("getInputMethodNavButtonFlagsLocked".equals(method.getName())) {
                methodGetInputMethodNavButtonFlagsLocked = method;
                break;
            }
        }
        if (methodGetInputMethodNavButtonFlagsLocked == null) {
            throw new NoSuchMethodException("getInputMethodNavButtonFlagsLocked");
        }
        recordHookHandle(hook(methodGetInputMethodNavButtonFlagsLocked)
                .setId(HOOK_NAV_BUTTON_FLAGS).intercept(this::interceptNavButtonFlags));
    }

    private void hookInputMethodManagerServiceImpl(ClassLoader classLoader)
            throws NoSuchMethodException, ClassNotFoundException {
        var classInputMethodManagerServiceImpl = classLoader.loadClass("com.android.server.inputmethod.InputMethodManagerServiceImpl");
        var methodIsCallingBetweenCustomIME = classInputMethodManagerServiceImpl.getDeclaredMethod("isCallingBetweenCustomIME", Context.class, int.class, String.class);
        recordHookHandle(hook(methodIsCallingBetweenCustomIME).setId(HOOK_CUSTOM_IME_CALLER)
                .intercept(this::interceptCustomImeCaller));
    }

    private void hookInputMethodBottomManager(ClassLoader classLoader) throws
            NoSuchMethodException,
            ClassNotFoundException {
        var classInputMethodModuleManager = classLoader.loadClass("android.inputmethodservice.InputMethodModuleManager");
        var methodLoadDex = classInputMethodModuleManager.getDeclaredMethod("loadDex",
                ClassLoader.class, String.class);
        recordHookHandle(hook(methodLoadDex).setId(HOOK_LOAD_BOTTOM_DEX)
                .intercept(this::interceptLoadBottomDex));
    }

    private void backfillHotReloadHooks(XposedModuleInterface.HotReloadedParam param,
                                        ClassLoader classLoader,
                                        Set<String> installedHookIds) {
        if (classLoader == null) {
            log(Log.WARN, TAG, "Cannot backfill hot reload hooks without a class loader");
            return;
        }
        if (param.isSystemServer() || "system".equals(param.getProcessName())) {
            backfillHook(HOOK_NAV_BUTTON_FLAGS, installedHookIds,
                    () -> hookInputMethodManagerService(classLoader));
            backfillHook(HOOK_CUSTOM_IME_CALLER, installedHookIds,
                    () -> hookInputMethodManagerServiceImpl(classLoader));
            return;
        }
        backfillHook(HOOK_HIDE_GESTURAL_BUTTONS, installedHookIds,
                () -> hookInputMethodService(classLoader));
        backfillHook(HOOK_CAPTION_BAR_HEIGHT, installedHookIds,
                () -> hookNavigationBarController(classLoader));
        try {
            if (usesStandaloneNavigationBarController(classLoader)) {
                backfillHook(HOOK_INPUT_VIEW_BOTTOM_INSET, installedHookIds,
                        () -> hookInputViewBottomInset(classLoader));
                backfillHook(HOOK_DISPATCH_INPUT_VIEW_INSETS, installedHookIds,
                        () -> hookInputViewInsetsDispatch(classLoader));
                backfillHook(HOOK_CAPTION_BAR_INSETS_HEIGHT, installedHookIds,
                        () -> hookCaptionBarInsetsHeight(classLoader));
                backfillHook(HOOK_SYSTEM_INSETS_HEIGHT, installedHookIds,
                        () -> hookNavigationBarSystemInsets(classLoader));
            }
        } catch (ClassNotFoundException e) {
            log(Log.WARN, TAG, "Unable to detect NavigationBarController generation", e);
        }
        backfillHook(HOOK_INFLATE_NAV_LAYOUT, installedHookIds,
                () -> hookNavigationBarInflaterView(classLoader));
        backfillHook(HOOK_UPDATE_ORIENTATION, installedHookIds,
                () -> hookNavigationBarView(classLoader));
        backfillHook(HOOK_DEAD_ZONE, installedHookIds,
                () -> hookDeadZone(classLoader));
        backfillHook(HOOK_LOAD_BOTTOM_DEX, installedHookIds,
                () -> hookInputMethodBottomManager(classLoader));
    }

    private void backfillHook(String hookId, Set<String> installedHookIds,
                              ThrowingRunnable installer) {
        if (installedHookIds.contains(hookId)) return;
        try {
            installer.run();
            installedHookIds.add(hookId);
            log(Log.INFO, TAG, "Backfilled hot reload hook " + hookId);
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "Unable to backfill hot reload hook " + hookId, throwable);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private synchronized void recordHookHandle(XposedInterface.HookHandle handle) {
        hookHandles.add(handle);
    }

    private synchronized void initializeNavBarLayoutPreferences() {
        releaseNavBarLayoutPreferences();
        var preferences = getRemotePreferences("conf");
        navBarLayoutHandle.set(preferences.getString("nav_bar_layout_handle", ""));
        SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
            if ("nav_bar_layout_handle".equals(key)) {
                String layout = prefs.getString(key, "");
                navBarLayoutHandle.set(layout);
                refreshNavBarLayouts(layout);
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);
        navBarPreferences = preferences;
        navBarPreferenceListener = listener;
    }

    private synchronized void releaseNavBarLayoutPreferences() {
        if (navBarPreferences != null && navBarPreferenceListener != null) {
            navBarPreferences.unregisterOnSharedPreferenceChangeListener(navBarPreferenceListener);
        }
        navBarPreferences = null;
        navBarPreferenceListener = null;
    }

    private static String identifyHook(Executable executable) {
        if (executable == null) return null;
        String owner = executable.getDeclaringClass().getName();
        return switch (owner + "#" + executable.getName()) {
            case "android.inputmethodservice.InputMethodService#hideImeRenderGesturalNavButtons" ->
                    HOOK_HIDE_GESTURAL_BUTTONS;
            case "android.inputmethodservice.NavigationBarController$Impl#getImeCaptionBarHeight",
                 "android.inputmethodservice.NavigationBarController#getImeCaptionBarHeight" ->
                    HOOK_CAPTION_BAR_HEIGHT;
            case "android.view.InsetsController#setImeCaptionBarInsetsHeight" ->
                    HOOK_CAPTION_BAR_INSETS_HEIGHT;
            case "android.inputmethodservice.NavigationBarController#getSystemInsets" ->
                    HOOK_SYSTEM_INSETS_HEIGHT;
            case "android.inputmethodservice.InputMethodService#setInputView" ->
                    HOOK_INPUT_VIEW_BOTTOM_INSET;
            case "android.view.View#dispatchApplyWindowInsets" ->
                    HOOK_DISPATCH_INPUT_VIEW_INSETS;
            case "android.inputmethodservice.navigationbar.NavigationBarInflaterView#inflateLayout" ->
                    HOOK_INFLATE_NAV_LAYOUT;
            case "android.inputmethodservice.navigationbar.NavigationBarView#updateOrientationViews" ->
                    HOOK_UPDATE_ORIENTATION;
            case "android.inputmethodservice.navigationbar.DeadZone#onConfigurationChanged" ->
                    HOOK_DEAD_ZONE;
            case "com.android.server.inputmethod.InputMethodManagerService#getInputMethodNavButtonFlagsLocked" ->
                    HOOK_NAV_BUTTON_FLAGS;
            case "com.android.server.inputmethod.InputMethodManagerServiceImpl#isCallingBetweenCustomIME" ->
                    HOOK_CUSTOM_IME_CALLER;
            case "android.inputmethodservice.InputMethodModuleManager#loadDex" ->
                    HOOK_LOAD_BOTTOM_DEX;
            case "com.miui.inputmethod.InputMethodBottomManager#getSupportIme" ->
                    HOOK_SUPPORTED_IME_LIST;
            default -> null;
        };
    }

    private XposedInterface.Hooker createHotReloadHooker(String hookId) {
        if (hookId == null) return null;
        return switch (hookId) {
            case HOOK_HIDE_GESTURAL_BUTTONS -> this::interceptHideGesturalButtons;
            case HOOK_CAPTION_BAR_HEIGHT -> this::interceptCaptionBarHeight;
            case HOOK_CAPTION_BAR_INSETS_HEIGHT -> this::interceptCaptionBarInsetsHeight;
            case HOOK_SYSTEM_INSETS_HEIGHT -> this::interceptNavigationBarSystemInsets;
            case HOOK_INPUT_VIEW_BOTTOM_INSET -> this::interceptInputViewBottomInset;
            case HOOK_DISPATCH_INPUT_VIEW_INSETS -> this::interceptInputViewInsetsDispatch;
            case HOOK_INFLATE_NAV_LAYOUT -> this::interceptInflateNavLayout;
            case HOOK_UPDATE_ORIENTATION -> this::interceptUpdateOrientation;
            case HOOK_DEAD_ZONE -> this::interceptDeadZone;
            case HOOK_NAV_BUTTON_FLAGS -> this::interceptNavButtonFlags;
            case HOOK_CUSTOM_IME_CALLER -> this::interceptCustomImeCaller;
            case HOOK_LOAD_BOTTOM_DEX -> this::interceptLoadBottomDex;
            case HOOK_SUPPORTED_IME_LIST -> this::interceptSupportedImeList;
            default -> null;
        };
    }

    private Object interceptHideGesturalButtons(XposedInterface.Chain chain) throws Throwable {
        try {
            var serviceClass = chain.getExecutable().getDeclaringClass();
            var stubClass = serviceClass.getClassLoader().loadClass(
                    "android.inputmethodservice.InputMethodServiceStub");
            var getInstance = stubClass.getDeclaredMethod("getInstance");
            var injector = getInvoker(getInstance).invoke(chain.getThisObject());
            if (injector != null) {
                var isImeSupport = injector.getClass().getDeclaredMethod("isImeSupport", Context.class);
                isImeSupport.setAccessible(true);
                if (chain.getThisObject() instanceof InputMethodService inputMethodService
                        && getInvoker(isImeSupport).invoke(injector,
                        inputMethodService.getApplicationContext()) instanceof Boolean supported
                        && !supported) {
                    return false;
                }
            }
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "hot reload hideImeRenderGesturalNavButtons", e);
        }
        return chain.proceed();
    }

    private Object interceptCaptionBarHeight(XposedInterface.Chain chain) throws Throwable {
        try {
            Object owner = chain.getThisObject();
            var ownerClass = chain.getExecutable().getDeclaringClass();
            var drawsNavBar = ownerClass.getDeclaredField("mImeDrawsImeNavBar");
            var service = ownerClass.getDeclaredField("mService");
            drawsNavBar.setAccessible(true);
            service.setAccessible(true);
            boolean shouldShow = !chain.getArgs().isEmpty()
                    && chain.getArg(0) instanceof Boolean value
                    ? value : drawsNavBar.getBoolean(owner);
            if (shouldShow && service.get(owner) instanceof InputMethodService inputMethodService) {
                int height = dpToPx(48, inputMethodService.getResources());
                return height;
            }
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "hot reload getImeCaptionBarHeight", e);
        }
        return chain.proceed();
    }

    private Object interceptCaptionBarInsetsHeight(XposedInterface.Chain chain) throws Throwable {
        if (!chain.getArgs().isEmpty() && chain.getArg(0) instanceof Integer height
                && height > 0) {
            int target = dpToPx(48, Resources.getSystem());
            if (height != target) {
                Object[] args = chain.getArgs().toArray();
                args[0] = target;
                return chain.proceed(args);
            }
        }
        return chain.proceed();
    }

    private Object interceptNavigationBarSystemInsets(XposedInterface.Chain chain)
            throws Throwable {
        Object result = chain.proceed();
        if (!(result instanceof Insets insets)) return result;
        try {
            Object owner = chain.getThisObject();
            var serviceField = chain.getExecutable().getDeclaringClass()
                    .getDeclaredField("mService");
            serviceField.setAccessible(true);
            if (serviceField.get(owner) instanceof InputMethodService inputMethodService) {
                int height = dpToPx(48, inputMethodService.getResources());
                return Insets.of(insets.left, insets.top, insets.right,
                        Math.max(insets.bottom, height));
            }
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "hot reload NavigationBarController.getSystemInsets", e);
        }
        return result;
    }

    private static final WeakHashMap<View, Boolean> INPUT_VIEWS = new WeakHashMap<>();

    private Object interceptInputViewBottomInset(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        if (!chain.getArgs().isEmpty() && chain.getArg(0) instanceof View inputView) {
            INPUT_VIEWS.put(inputView, Boolean.TRUE);
            inputView.requestApplyInsets();
        }
        return result;
    }

    private Object interceptInputViewInsetsDispatch(XposedInterface.Chain chain) throws Throwable {
        if (chain.getThisObject() instanceof View view && INPUT_VIEWS.containsKey(view)
                && !chain.getArgs().isEmpty()
                && chain.getArg(0) instanceof WindowInsets insets) {
            Insets navigationBars = insets.getInsets(WindowInsets.Type.navigationBars());
            Insets captionBar = insets.getInsets(WindowInsets.Type.captionBar());
            int bottom = Math.max(navigationBars.bottom, captionBar.bottom);
            if (bottom != navigationBars.bottom) {
                WindowInsets mergedInsets = new WindowInsets.Builder(insets)
                        .setInsets(WindowInsets.Type.navigationBars(), Insets.of(
                                navigationBars.left, navigationBars.top,
                                navigationBars.right, bottom))
                        .build();
                Object[] args = chain.getArgs().toArray();
                args[0] = mergedInsets;
                return chain.proceed(args);
            }
        }
        return chain.proceed();
    }

    private Object interceptInflateNavLayout(XposedInterface.Chain chain) throws Throwable {
        Object owner = chain.getThisObject();
        if (owner instanceof View inflaterView) {
            synchronized (ACTIVE_NAV_BAR_LAYOUTS) {
                if (!ACTIVE_NAV_BAR_LAYOUTS.containsKey(inflaterView)) {
                    ACTIVE_NAV_BAR_LAYOUTS.put(inflaterView,
                            chain.getArg(0) instanceof String layout ? layout : null);
                }
            }
        }
        if (chain.getArg(0) instanceof String && !navBarLayoutHandle.get().isBlank()) {
            Object[] args = chain.getArgs().toArray();
            args[0] = navBarLayoutHandle.get();
            return chain.proceed(args);
        }
        return chain.proceed();
    }

    private void refreshNavBarLayouts(String configuredLayout) {
        List<View> views = new ArrayList<>();
        List<String> defaultLayouts = new ArrayList<>();
        synchronized (ACTIVE_NAV_BAR_LAYOUTS) {
            ACTIVE_NAV_BAR_LAYOUTS.forEach((view, defaultLayout) -> {
                views.add(view);
                defaultLayouts.add(defaultLayout);
            });
        }
        for (int index = 0; index < views.size(); index++) {
            View view = views.get(index);
            String defaultLayout = defaultLayouts.get(index);
            view.post(() -> reinflateNavBarLayout(view,
                    configuredLayout.isBlank() ? defaultLayout : configuredLayout));
        }
    }

    private void reinflateNavBarLayout(View view, String layout) {
        if (!view.isAttachedToWindow()) return;
        try {
            Class<?> inflaterClass = view.getClass();
            Method clearViews = inflaterClass.getDeclaredMethod("clearViews");
            Method inflateLayout = inflaterClass.getDeclaredMethod("inflateLayout", String.class);
            clearViews.setAccessible(true);
            inflateLayout.setAccessible(true);
            getInvoker(clearViews).invoke(view);
            getInvoker(inflateLayout).invoke(view, layout);
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "refresh NavigationBarInflaterView layout", e);
        }
    }

    private Object interceptUpdateOrientation(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        Object owner = chain.getThisObject();
        if (owner == null) return result;
        try {
            var horizontal = chain.getExecutable().getDeclaringClass().getDeclaredField("mHorizontal");
            horizontal.setAccessible(true);
            if (horizontal.get(owner) instanceof View horizontalView) {
                int shadow = dpToPx(4, horizontalView.getResources());
                horizontalView.setOnApplyWindowInsetsListener((view, insets) -> {
                    var base = BASE_PADDINGS.computeIfAbsent(view, value -> new int[]{
                            value.getPaddingLeft() + shadow, value.getPaddingTop(),
                            value.getPaddingRight() + shadow, value.getPaddingBottom()
                    });
                    var left = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
                    var right = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
                    int leftRadius = left != null ? left.getRadius() : 0;
                    int rightRadius = right != null ? right.getRadius() : 0;
                    view.setPadding(leftRadius > 0 ? leftRadius - base[0] : base[0], base[1],
                            rightRadius > 0 ? rightRadius - base[2] : base[2], base[3]);
                    return insets;
                });
            }
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "hot reload updateOrientationViews", e);
        }
        return result;
    }

    private Object interceptDeadZone(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            var sizeMin = chain.getExecutable().getDeclaringClass().getDeclaredField("mSizeMin");
            sizeMin.setAccessible(true);
            if (chain.getThisObject() != null) sizeMin.setInt(chain.getThisObject(), 0);
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "hot reload DeadZone.onConfigurationChanged", e);
        }
        return result;
    }

    private Object interceptNavButtonFlags(XposedInterface.Chain chain) throws Throwable {
        if (!chain.getArgs().isEmpty() && chain.getArg(0) != null) {
            try {
                Object owner = chain.getThisObject();
                Object userData = chain.getArg(0);
                var ownerClass = chain.getExecutable().getDeclaringClass();
                var contextField = ownerClass.getDeclaredField("mContext");
                var bindingField = userData.getClass().getDeclaredField("mBindingController");
                var drawsNavBarField = userData.getClass().getDeclaredField("mImeDrawsNavBar");
                contextField.setAccessible(true);
                bindingField.setAccessible(true);
                drawsNavBarField.setAccessible(true);
                Object bindingController = bindingField.get(userData);
                var getSelectedImeId = bindingController.getClass().getDeclaredMethod(
                        "getSelectedImeId");
                getSelectedImeId.setAccessible(true);
                Object selectedImeId = getInvoker(getSelectedImeId).invoke(bindingController);
                var stubClass = ownerClass.getClassLoader().loadClass(
                        "com.android.server.inputmethod.InputMethodManagerServiceStub");
                var getInstance = stubClass.getDeclaredMethod("getInstance");
                Object implementation = getInvoker(getInstance).invoke(owner);
                if (implementation != null && selectedImeId instanceof String imeId
                        && contextField.get(owner) instanceof Context context) {
                    var isCustomized = implementation.getClass().getDeclaredMethod(
                            "isCustomizedInputMethod", String.class);
                    isCustomized.setAccessible(true);
                    boolean gestures = Settings.Secure.getInt(
                            context.getContentResolver(), "navigation_mode", 2) == 2;
                    boolean customized = getInvoker(isCustomized).invoke(implementation, imeId)
                            instanceof Boolean value && value;
                    if (gestures && !customized
                            && drawsNavBarField.get(userData) instanceof AtomicBoolean drawsNavBar) {
                        drawsNavBar.set(true);
                    }
                }
            } catch (ReflectiveOperationException e) {
                log(Log.ERROR, TAG, "Android 17 getInputMethodNavButtonFlagsLocked", e);
            }
            return chain.proceed();
        }
        try {
            Object owner = chain.getThisObject();
            var ownerClass = chain.getExecutable().getDeclaringClass();
            var drawsNavBarResource = ownerClass.getDeclaredField("mImeDrawsImeNavBarRes");
            var settingsField = ownerClass.getDeclaredField("mSettings");
            var contextField = ownerClass.getDeclaredField("mContext");
            drawsNavBarResource.setAccessible(true);
            settingsField.setAccessible(true);
            contextField.setAccessible(true);
            var wrapperClass = ownerClass.getClassLoader().loadClass(
                    "com.android.server.inputmethod.OverlayableSystemBooleanResourceWrapper");
            var valueReference = wrapperClass.getDeclaredField("mValueRef");
            valueReference.setAccessible(true);
            var stubClass = ownerClass.getClassLoader().loadClass(
                    "com.android.server.inputmethod.InputMethodManagerServiceStub");
            var getInstance = stubClass.getDeclaredMethod("getInstance");
            Object implementation = getInvoker(getInstance).invoke(owner);
            Object settings = settingsField.get(owner);
            if (implementation != null && settings != null
                    && contextField.get(owner) instanceof Context context) {
                var isCustomized = implementation.getClass().getDeclaredMethod(
                        "isCustomizedInputMethod", String.class);
                var getSelected = settings.getClass().getDeclaredMethod("getSelectedInputMethod");
                isCustomized.setAccessible(true);
                getSelected.setAccessible(true);
                boolean gestures = Settings.Secure.getInt(
                        context.getContentResolver(), "navigation_mode", 2) == 2;
                boolean canDraw = gestures
                        && getInvoker(isCustomized).invoke(implementation,
                        getSelected.invoke(settings)) instanceof Boolean customized
                        && !customized;
                try {
                    if (context.getSystemService(Context.OVERLAY_SERVICE)
                            instanceof OverlayManager overlayManager) {
                        String overlay = "com.android.internal.systemui.navbar.gestural";
                        var info = HiddenApiBridge.OverlayManager_getOverlayInfo(overlayManager,
                                overlay, HiddenApiBridge.UserHandle_CURRENT());
                        if (info != null && HiddenApiBridge.OverlayInfo_isEnabled(info) != canDraw) {
                            HiddenApiBridge.OverlayManager_setEnabled(overlayManager, overlay,
                                    canDraw, HiddenApiBridge.UserHandle_CURRENT());
                        }
                    }
                } catch (SecurityException | IllegalStateException e) {
                    log(Log.ERROR, TAG, "Failed to toggle gestural nav overlay", e);
                }
                Object wrapper = drawsNavBarResource.get(owner);
                if (valueReference.get(wrapper) instanceof AtomicBoolean value) {
                    value.set(canDraw);
                }
            }
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "hot reload getInputMethodNavButtonFlagsLocked", e);
        }
        return chain.proceed();
    }

    private Object interceptCustomImeCaller(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        var args = chain.getArgs();
        if (result instanceof Boolean customized && !customized && args.size() >= 3
                && args.get(0) instanceof Context context && args.get(1) instanceof Integer uid) {
            var manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            var current = manager.getCurrentInputMethodInfo();
            if (current != null) {
                String[] packages = context.getPackageManager().getPackagesForUid(uid);
                if (packages != null) {
                    for (String packageName : packages) {
                        if (current.getPackageName().equals(packageName)) return true;
                    }
                }
            }
        }
        return result;
    }

    private Object interceptLoadBottomDex(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            if (!chain.getArgs().isEmpty() && chain.getArg(0) instanceof ClassLoader classLoader) {
                installSupportedImeListHook(classLoader);
            }
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "hot reload loadDex", e);
        }
        return result;
    }

    private void installSupportedImeListHook(ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException {
        var managerClass = classLoader.loadClass("com.miui.inputmethod.InputMethodBottomManager");
        var getSupportIme = managerClass.getDeclaredMethod("getSupportIme");
        recordHookHandle(hook(getSupportIme).setId(HOOK_SUPPORTED_IME_LIST)
                .intercept(this::interceptSupportedImeList));
    }

    private Object interceptSupportedImeList(XposedInterface.Chain chain) throws Throwable {
        try {
            var managerClass = chain.getExecutable().getDeclaringClass();
            var helperClass = managerClass.getClassLoader().loadClass(
                    "com.miui.inputmethod.InputMethodBottomManager$BottomViewHelper");
            var helperImm = helperClass.getDeclaredField("mImm");
            var staticHelper = managerClass.getDeclaredField("sBottomViewHelper");
            helperImm.setAccessible(true);
            staticHelper.setAccessible(true);
            Object helper = staticHelper.get(chain.getThisObject());
            if (helper != null && helperImm.get(helper) instanceof InputMethodManager manager) {
                return manager.getEnabledInputMethodList();
            }
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "hot reload getSupportIme", e);
        }
        return chain.proceed();
    }
}
