package run.halo.alist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * <p>Plugin main class to manage the lifecycle of the plugin.</p>
 * <p>This class must be public and have a public constructor.</p>
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.</p>
 *
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/3
 */
@Slf4j
@Component
public class AListPlugin extends BasePlugin {

    public AListPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        log.info("AList 插件启动成功！");
    }

    @Override
    public void stop() {
        log.info("AList 插件停止！");
    }

    @Override
    public void delete() {
        log.info("AList 插件被删除！");
    }
}
