package com.xulai.keyswitch;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue LONG_PRESS_MILLIS = BUILDER
            .comment("按住共用键多少毫秒后，滚动滚轮才会呼出切换卡片（单独长按不做任何事，方便那些需要长按触发的模组） # How long a shared key must be held before scrolling the wheel opens the switcher card; holding the key alone does nothing, so hold-to-activate mods keep working")
            .defineInRange("longPressMillis", 400, 120, 3000);

    public static final ModConfigSpec.BooleanValue WORK_INSIDE_GUI = BUILDER
            .comment("打开背包、箱子等界面时是否仍然响应长按滚轮切换（聊天框等输入框获得焦点时永远不会触发） # Whether holding a key inside a screen still starts the wheel switcher (text input fields always keep priority)")
            .define("workInsideGui", true);

    public static final ModConfigSpec.BooleanValue MOUSE_BUTTON_LONG_PRESS = BUILDER
            .comment("是否允许按住鼠标键进入滚轮切换；关闭后鼠标按键冲突只能在总览界面里切换 # Whether holding a mouse button starts the wheel switcher; when off, mouse button conflicts can only be switched from the overview screen")
            .define("mouseButtonLongPress", false);

    public static final ModConfigSpec.BooleanValue SWALLOW_HELD_TRIGGER = BUILDER
            .comment("默认关：按下共用键时不动它，按下即触发照常，长按/按住型模组也照常工作；滚轮切换时当前功能已经触发过一次。打开则按住期间吞掉这个键的所有触发（连当前选中的也不会误触发），代价是共用键的短按改为松手才触发、按住型按键会失效 # Off by default: presses on a shared key are left untouched, so press-to-trigger and hold-to-activate mods behave normally (the current binding fires once before you scroll). Turn it on to swallow the key while held so nothing on it fires, at the cost of taps firing on release and hold-to-activate bindings breaking")
            .define("swallowHeldTrigger", false);

    public static final ModConfigSpec.BooleanValue PAUSE_WHILE_CHOOSING = BUILDER
            .comment("打开切换总览界面时是否像原版界面那样暂停单人游戏 # Whether the overview pauses singleplayer like a regular inventory screen")
            .define("pauseWhileChoosing", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_BINDINGS = BUILDER
            .comment("控制键白名单：这些绑定永远不会被替换成无按键，并且只要一个按键上挂着其中任何一项，长按这个按键就不会进入滚轮切换（改用总览键切换）；填绑定名，例如 key.jump # Control key whitelist: these bindings are never replaced by an unbound key, and a key carrying any of them never starts wheel switching on long press (use the overview key instead); use binding names such as key.jump")
            .defineListAllowEmpty("protectedBindings",
                    List.of("key.forward", "key.left", "key.back", "key.right", "key.jump", "key.sneak", "key.sprint",
                            "key.use", "key.attack", "key.pickItem"),
                    () -> "key.jump",
                    value -> value instanceof String name && !name.isBlank());

    public static final ModConfigSpec.BooleanValue SHOW_SWITCH_TOAST = BUILDER
            .comment("切换完成后在画面中央提示当前生效的绑定 # Show which binding is active after switching")
            .define("showSwitchToast", true);

    public static final ModConfigSpec.BooleanValue SHOW_CONFLICT_HINT = BUILDER
            .comment("进入世界时在聊天栏提示检测到的按键冲突数量 # Print how many conflicting keys were found into chat when entering a world")
            .define("showConflictHint", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
