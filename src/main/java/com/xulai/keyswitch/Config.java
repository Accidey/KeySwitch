package com.xulai.keyswitch;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        LONG_PRESS_MILLIS = BUILDER
                .comment(
                        "长按判定毫秒",
                        "Long press threshold in milliseconds",
                        "按住共用键超过这个时间后，滚动滚轮才会呼出切换卡片。单独长按不会做任何事。",
                        "The wheel switcher opens only when the wheel is scrolled after a shared key has been held for this long. Holding the key alone does nothing.",
                        "Default: 400 / 默认：400",
                        "Range: 120 ~ 3000"
                )
                .defineInRange("longPressMillis", 400, 120, 3000);

        BUILDER.comment(" ");

        WORK_INSIDE_GUI = BUILDER
                .comment(
                        "界面内也生效",
                        "Work inside screens",
                        "打开背包、箱子等界面时，长按共用键再滚动滚轮仍然可以呼出切换卡片。输入框获得焦点时永远不触发。",
                        "Holding a shared key and scrolling the wheel still opens the switcher inside screens such as the inventory or a chest. Text input fields always keep priority.",
                        "Default: true / 默认：true"
                )
                .define("workInsideGui", true);

        BUILDER.comment(" ");

        MOUSE_BUTTON_LONG_PRESS = BUILDER
                .comment(
                        "支持鼠标长按",
                        "Mouse button long press",
                        "允许按住鼠标键进入滚轮切换。关闭后，鼠标键冲突只能在总览界面里切换。",
                        "Whether holding a mouse button can start the wheel switcher. When disabled, mouse button conflicts can only be switched from the overview screen.",
                        "Default: false / 默认：false"
                )
                .define("mouseButtonLongPress", false);

        BUILDER.comment(" ");

        PAUSE_WHILE_CHOOSING = BUILDER
                .comment(
                        "总览时暂停",
                        "Pause while choosing",
                        "打开切换总览界面时，是否像原版界面一样暂停单人游戏。",
                        "Whether the overview pauses singleplayer like a regular inventory screen.",
                        "Default: false / 默认：false"
                )
                .define("pauseWhileChoosing", false);

        BUILDER.comment(" ");

        PROTECTED_BINDINGS = BUILDER
                .comment(
                        "控制键白名单",
                        "Protected bindings",
                        "这些绑定永远不会被替换成无按键；只要一个按键上挂着其中任意一项，长按这个按键就不会进入滚轮切换，请改用总览键切换。",
                        "These bindings are never replaced by an unbound key, and a key carrying any of them never starts wheel switching; use the overview key instead.",
                        "Default: [key.forward, key.left, key.back, key.right, key.jump, key.sneak, key.sprint, key.use, key.attack, key.pickItem] / 默认：[key.forward, key.left, key.back, key.right, key.jump, key.sneak, key.sprint, key.use, key.attack, key.pickItem]"
                )
                .defineListAllowEmpty(
                        "protectedBindings",
                        List.of("key.forward", "key.left", "key.back", "key.right", "key.jump", "key.sneak", "key.sprint",
                                "key.use", "key.attack", "key.pickItem"),
                        value -> value instanceof String
                );

        BUILDER.comment(" ");

        SHOW_SWITCH_TOAST = BUILDER
                .comment(
                        "显示切换提示",
                        "Show switch toast",
                        "切换完成后，在画面中央和聊天栏提示当前生效的绑定。",
                        "Show which binding is active in the centre of the screen and in chat after switching.",
                        "Default: true / 默认：true"
                )
                .define("showSwitchToast", true);

        BUILDER.comment(" ");

        SHOW_CONFLICT_HINT = BUILDER
                .comment(
                        "显示冲突提示",
                        "Show conflict hint",
                        "进入世界时，在聊天栏提示检测到的共用按键数量。",
                        "Print how many shared keys were found into chat when entering a world.",
                        "Default: true / 默认：true"
                )
                .define("showConflictHint", true);
    }

    public static final ForgeConfigSpec.IntValue LONG_PRESS_MILLIS;

    public static final ForgeConfigSpec.BooleanValue WORK_INSIDE_GUI;

    public static final ForgeConfigSpec.BooleanValue MOUSE_BUTTON_LONG_PRESS;

    public static final ForgeConfigSpec.BooleanValue PAUSE_WHILE_CHOOSING;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROTECTED_BINDINGS;

    public static final ForgeConfigSpec.BooleanValue SHOW_SWITCH_TOAST;

    public static final ForgeConfigSpec.BooleanValue SHOW_CONFLICT_HINT;

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
