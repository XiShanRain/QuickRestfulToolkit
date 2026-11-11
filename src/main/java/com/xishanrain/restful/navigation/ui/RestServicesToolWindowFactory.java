package com.xishanrain.restful.navigation.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

/**
 * RestfulRadar 工具窗口工厂。
 *
 * <p>必须保持为纯 Java 实现：Kotlin 类实现 {@link ToolWindowFactory} 时，编译器会为接口的
 * 所有 default 方法（{@code getAnchor}/{@code getIcon}/{@code manage} 等）生成转发桥接方法，
 * 而这些方法在部分 IDE 版本（如 2024.3、2025.1）中标记为 {@code @ApiStatus.Internal}，
 * 会被 Plugin Verifier 判定为“使用内部 API”。Java 实现只编译显式重写的方法，
 * 不会生成这类桥接方法，因此不会触发该检查。</p>
 *
 * <p>面板本身与静态入口保留在 {@link RestServicesPanel} 中，这里只负责创建内容。</p>
 */
public final class RestServicesToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        RestServicesPanel.attachTo(project, toolWindow);
    }

    /** 供主入口 / 切换动作调用：展开工具窗口并把焦点放到 URL 过滤框。 */
    public static boolean activateAndFocusFilter(@NotNull Project project) {
        return RestServicesPanel.activateAndFocusFilter(project);
    }
}
