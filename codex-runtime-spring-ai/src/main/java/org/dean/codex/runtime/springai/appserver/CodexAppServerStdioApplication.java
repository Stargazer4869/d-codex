package org.dean.codex.runtime.springai.appserver;

import org.dean.codex.core.appserver.CodexAppServer;
import org.dean.codex.runtime.springai.NettyRuntimeWarmup;
import org.dean.codex.runtime.springai.appserver.transport.jsonrpc.StdioJsonRpcAppServerLauncher;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@SpringBootApplication(scanBasePackages = "org.dean.codex")
public class CodexAppServerStdioApplication {

    public static void main(String[] args) {
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");
        NettyRuntimeWarmup.preloadShutdownSensitiveNettyClasses();
        PrintStream protocolStdout = System.out;
        System.setOut(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        SpringApplication application = new SpringApplication(CodexAppServerStdioApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setWebApplicationType(WebApplicationType.NONE);

        ConfigurableApplicationContext context = application.run(args);
        try {
            CodexAppServer appServer = context.getBean(CodexAppServer.class);
            new StdioJsonRpcAppServerLauncher(appServer).run(System.in, protocolStdout);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to run stdio app-server host.", exception);
        }
        finally {
            context.close();
        }
    }
}
