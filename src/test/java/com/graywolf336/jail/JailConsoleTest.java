package com.graywolf336.jail;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import com.graywolf336.jail.beans.*;
import com.graywolf336.jail.enums.Settings;
import test.java.com.graywolf336.jail.util.TestInstanceCreator;

public class JailConsoleTest {
    private TestInstanceCreator fixture;
    private JailMain plugin;
    private Logger logger;
    private Prisoner prisoner;
    @Before public void setup() {
        fixture = new TestInstanceCreator(); assertTrue(fixture.setup()); plugin = fixture.getMain();
        logger = mock(Logger.class); doReturn(logger).when(plugin).getLogger();
        prisoner = new Prisoner(fixture.getPlayer().getUniqueId().toString(), "Alex", 1800000L);
    }
    @After public void cleanup() { fixture.tearDown(); }

    @Test public void formatCannotInjectLinesAnsiOrAdditionalFields() {
        String line = JailConsole.format("JAILED", "player", "Alex\n[ERROR]", "reason", "bad\" | actor=Evil\r\n\u001b[31m", "actor", null);
        assertFalse(line.contains("\n")); assertFalse(line.contains("\r")); assertFalse(line.contains("\u001b"));
        assertFalse(line.contains("[31m")); assertTrue(line.contains("reason=\"bad\\\" | actor=Evil"));
        assertFalse(line.endsWith("actor=null"));
    }

    @Test public void durationsAreConciseAndPermanentSentencesAreClear() {
        assertEquals("30m", JailConsole.duration(1800000)); assertEquals("permanent", JailConsole.duration(-1));
        assertEquals("1d2h3m4s", JailConsole.duration(93784000)); assertEquals("0s", JailConsole.duration(0));
    }

    @Test public void bannerIncludesForkVersionAndOriginalCreditWithoutAnsi() {
        plugin.console().banner();
        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class); verify(logger, times(9)).info(lines.capture());
        String output = String.join("\n", lines.getAllValues());
        assertTrue(output.contains("J A I L - R E L O A D E D")); assertTrue(output.contains("v1.0.0"));
        assertTrue(output.contains("Originally By: graywolf336")); assertTrue(output.contains("Rismr1 | ImagineCraft Plugins"));
        assertFalse(output.contains("\u001b"));
    }

    @Test public void reloadDoesNotRepeatTheBanner() throws Exception {
        plugin.reloadEverything(); plugin.reloadEverything();
        verify(logger, times(2)).info(startsWith("Reload complete in "));
        verify(logger, never()).info(contains("J A I L - R E L O A D E D"));
    }

    @Test public void playerLogsHonorConfigurationAndKeepUuidInDebugOnly() {
        plugin.setDebugging(false);
        plugin.console().player("JAILED", prisoner, "reason", "Griefing");
        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class); verify(logger).info(lines.capture());
        assertEquals("JAILED | player=Alex | reason=\"Griefing\"", lines.getValue());
        assertFalse(lines.getValue().contains(prisoner.getUUID().toString()));
        plugin.getConfig().set(Settings.LOGJAILINGTOCONSOLE.getPath(), false); clearInvocations(logger);
        plugin.console().player("RELEASED", prisoner); verify(logger, never()).info(anyString());
    }

    @Test public void releaseCauseAndActorAreOnlyIncludedWhenKnown() {
        plugin.setDebugging(false); fixture.addJail("audit"); Jail jail = plugin.getJailManager().getJail("audit");
        plugin.console().released(prisoner, jail, null, null);
        verify(logger).info(contains("cause=scheduled_release")); verify(logger, never()).info(contains("actor="));
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        plugin.console().rememberRelease(prisoner, console, "admin_release");
        plugin.console().released(prisoner, jail, null, null);
        verify(logger).info(contains("actor=CONSOLE | cause=admin_release"));
    }

    @Test public void repetitiveDenialsAreDebugOnlyAndRateLimitedUntilQuit() {
        for(int i = 0; i < 10; i++) plugin.console().denied(prisoner.getUUID(), "Alex", "CHEST_DENIED", "reason", "read_only");
        verify(logger, times(1)).info(startsWith("[DEBUG] CHEST_DENIED"));
        plugin.console().forget(prisoner.getUUID());
        plugin.console().denied(prisoner.getUUID(), "Alex", "CHEST_DENIED");
        verify(logger, times(2)).info(startsWith("[DEBUG] CHEST_DENIED"));
        plugin.setDebugging(false); clearInvocations(logger);
        plugin.console().denied(UUID.randomUUID(), "Other", "CHEST_DENIED"); verify(logger, never()).info(anyString());
    }

    @Test public void failedPropertyRestoreNeverLogsASuccessfulRelease() throws Exception {
        fixture.addJail("audit"); Jail jail = plugin.getJailManager().getJail("audit"); jail.addPrisoner(prisoner);
        prisoner.setInventory("JAIL2N:broken");
        try { plugin.getPrisonerManager().unJail(jail, null, fixture.getPlayer(), prisoner, null); fail("Expected corrupt property rejection"); }
        catch(IllegalStateException expected) { }
        assertTrue(jail.isPlayerJailed(prisoner.getUUID()));
        verify(logger, never()).info(startsWith("RELEASED"));
    }

    @Test public void failedStartupDoesNotSavePartiallyLoadedJailsOrClaimSuccessfulShutdown() throws Exception {
        fixture.addJail("partial");
        JailIO io = spy(plugin.getJailIO());
        java.lang.reflect.Field ioField = JailMain.class.getDeclaredField("io"); ioField.setAccessible(true); ioField.set(plugin, io);
        java.lang.reflect.Field started = JailMain.class.getDeclaredField("started"); started.setAccessible(true); started.set(plugin, false);
        plugin.onDisable();
        verify(io, never()).saveJail(any(Jail.class));
        verify(logger, never()).info(startsWith("Disabled | saved"));
    }
}
