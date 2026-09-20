// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.microsoft.copilot.eclipse.core.chat.CustomChatMode;
import com.microsoft.copilot.eclipse.core.lsp.protocol.ConversationMode;

class CustomAgentToolStatusResolverTest {

  private static final String BUILT_IN_TOOLS = "Built-in Tools";
  private static final String CUSTOM_MODE_ID = "file:///workspace/.github/agents/test.agent.md";
  private static final Map<String, Map<String, Boolean>> AVAILABLE_TOOLS = Map.of(
      BUILT_IN_TOOLS, Map.of("read_file", true, "run_in_terminal", true),
      "company/platform", Map.of("search", true, "inspect", true));

  @Test
  void testSynchronizeModePreferences_missingToolList_enablesAllAvailableToolsWithoutGlobalInheritance() {
    CustomChatMode mode = createMode(CUSTOM_MODE_ID, null);
    Map<String, Map<String, Map<String, Boolean>>> modeToolStatus = new LinkedHashMap<>();
    modeToolStatus.put("agent-mode", Map.of(BUILT_IN_TOOLS, Map.of("read_file", false)));

    boolean changed = CustomAgentToolStatusResolver.synchronizeModePreferences(modeToolStatus,
        List.of(mode), BUILT_IN_TOOLS, AVAILABLE_TOOLS);

    assertTrue(changed);
    assertEquals(AVAILABLE_TOOLS, modeToolStatus.get(CUSTOM_MODE_ID));
    assertEquals(Map.of(BUILT_IN_TOOLS, Map.of("read_file", false)), modeToolStatus.get("agent-mode"));
  }

  @Test
  void testResolve_toolList_usesExactlyCustomAgentTools() {
    CustomChatMode mode = createMode(CUSTOM_MODE_ID,
        List.of("read_file", "company/platform/search", "", "/invalid"));

    Map<String, Map<String, Boolean>> resolvedStatus = CustomAgentToolStatusResolver.resolve(mode, BUILT_IN_TOOLS,
        AVAILABLE_TOOLS);

    Map<String, Map<String, Boolean>> expectedStatus = new LinkedHashMap<>();
    expectedStatus.put(BUILT_IN_TOOLS, Map.of("read_file", true, "run_in_terminal", false));
    expectedStatus.put("company/platform", Map.of("search", true, "inspect", false));
    assertEquals(expectedStatus, resolvedStatus);
  }

  @Test
  void testSynchronizeModePreferences_missingToolList_existingPreferencesRemainAuthoritative() {
    CustomChatMode mode = createMode(CUSTOM_MODE_ID, null);
    Map<String, Map<String, Boolean>> savedStatus = new LinkedHashMap<>();
    savedStatus.put(BUILT_IN_TOOLS, new LinkedHashMap<>(Map.of("run_in_terminal", true)));
    Map<String, Map<String, Map<String, Boolean>>> modeToolStatus = new LinkedHashMap<>();
    modeToolStatus.put(CUSTOM_MODE_ID, savedStatus);

    boolean changed = CustomAgentToolStatusResolver.synchronizeModePreferences(modeToolStatus,
        List.of(mode), BUILT_IN_TOOLS, AVAILABLE_TOOLS);

    assertFalse(changed);
    assertSame(savedStatus, modeToolStatus.get(CUSTOM_MODE_ID));
  }

  @Test
  void testSynchronizeModePreferences_explicitToolList_overwritesSavedPreferences() {
    CustomChatMode mode = createMode(CUSTOM_MODE_ID, List.of("read_file"));
    Map<String, Map<String, Map<String, Boolean>>> modeToolStatus = new LinkedHashMap<>();
    modeToolStatus.put(CUSTOM_MODE_ID,
        Map.of(BUILT_IN_TOOLS, Map.of("read_file", false, "run_in_terminal", true)));

    boolean changed = CustomAgentToolStatusResolver.synchronizeModePreferences(modeToolStatus,
        List.of(mode), BUILT_IN_TOOLS, AVAILABLE_TOOLS);

    assertTrue(changed);
    assertEquals(Map.of(
        BUILT_IN_TOOLS, Map.of("read_file", true, "run_in_terminal", false),
        "company/platform", Map.of("search", false, "inspect", false)), modeToolStatus.get(CUSTOM_MODE_ID));
  }

  @Test
  void testSynchronizeModePreferences_explicitEmptyToolList_clearsSavedPreferences() {
    CustomChatMode mode = createMode(CUSTOM_MODE_ID, List.of());
    Map<String, Map<String, Map<String, Boolean>>> modeToolStatus = new LinkedHashMap<>();
    modeToolStatus.put(CUSTOM_MODE_ID, Map.of(BUILT_IN_TOOLS, Map.of("read_file", true)));

    boolean changed = CustomAgentToolStatusResolver.synchronizeModePreferences(modeToolStatus,
        List.of(mode), BUILT_IN_TOOLS, AVAILABLE_TOOLS);

    assertTrue(changed);
    assertEquals(Map.of(
        BUILT_IN_TOOLS, Map.of("read_file", false, "run_in_terminal", false),
        "company/platform", Map.of("search", false, "inspect", false)), modeToolStatus.get(CUSTOM_MODE_ID));
  }

  @Test
  void testSynchronizeModePreferences_newAgentTemplate_clearsSavedPreferences() {
    CustomChatMode mode = new CustomChatMode(CUSTOM_MODE_ID, "Test Agent", "Description");
    Map<String, Map<String, Map<String, Boolean>>> modeToolStatus = new LinkedHashMap<>();
    modeToolStatus.put(CUSTOM_MODE_ID, Map.of(BUILT_IN_TOOLS, Map.of("read_file", true)));

    boolean changed = CustomAgentToolStatusResolver.synchronizeModePreferences(modeToolStatus,
        List.of(mode), BUILT_IN_TOOLS, AVAILABLE_TOOLS);

    assertTrue(changed);
    assertEquals(Map.of(
        BUILT_IN_TOOLS, Map.of("read_file", false, "run_in_terminal", false),
        "company/platform", Map.of("search", false, "inspect", false)), modeToolStatus.get(CUSTOM_MODE_ID));
  }

  @Test
  void testSynchronizeModePreferences_missingToolList_preservesExistingCustomPreferences() {
    CustomChatMode mode = createMode(CUSTOM_MODE_ID, null);
    Map<String, Map<String, Boolean>> savedStatus = new LinkedHashMap<>();
    savedStatus.put(BUILT_IN_TOOLS, new LinkedHashMap<>(Map.of("read_file", false)));
    Map<String, Map<String, Map<String, Boolean>>> modeToolStatus = new LinkedHashMap<>();
    modeToolStatus.put(CUSTOM_MODE_ID, savedStatus);

    boolean changed = CustomAgentToolStatusResolver.synchronizeModePreferences(modeToolStatus,
        List.of(mode), BUILT_IN_TOOLS, AVAILABLE_TOOLS);

    assertFalse(changed);
    assertSame(savedStatus, modeToolStatus.get(CUSTOM_MODE_ID));
    assertEquals(Map.of(BUILT_IN_TOOLS, Map.of("read_file", false)), modeToolStatus.get(CUSTOM_MODE_ID));
  }

  @Test
  void testSynchronizeModePreferences_multipleCustomAgentsRemainIsolated() {
    String secondModeId = "file:///workspace/.github/agents/second.agent.md";
    CustomChatMode firstMode = createMode(CUSTOM_MODE_ID, List.of("read_file"));
    CustomChatMode secondMode = createMode(secondModeId, List.of("run_in_terminal"));
    Map<String, Map<String, Map<String, Boolean>>> modeToolStatus = new LinkedHashMap<>();

    CustomAgentToolStatusResolver.synchronizeModePreferences(modeToolStatus,
        List.of(firstMode, secondMode), BUILT_IN_TOOLS, AVAILABLE_TOOLS);

    assertEquals(Map.of(
        BUILT_IN_TOOLS, Map.of("read_file", true, "run_in_terminal", false),
        "company/platform", Map.of("search", false, "inspect", false)), modeToolStatus.get(CUSTOM_MODE_ID));
    assertEquals(Map.of(
        BUILT_IN_TOOLS, Map.of("read_file", false, "run_in_terminal", true),
        "company/platform", Map.of("search", false, "inspect", false)), modeToolStatus.get(secondModeId));
  }

  @Test
  void testSynchronizeModePreferences_missingToolList_waitsForAvailableToolInventory() {
    CustomChatMode mode = createMode(CUSTOM_MODE_ID, null);
    Map<String, Map<String, Map<String, Boolean>>> modeToolStatus = new LinkedHashMap<>();

    boolean changed = CustomAgentToolStatusResolver.synchronizeModePreferences(modeToolStatus,
        List.of(mode), BUILT_IN_TOOLS, null);

    assertFalse(changed);
    assertFalse(modeToolStatus.containsKey(CUSTOM_MODE_ID));
  }

  @Test
  void testAllowsToolConfiguration_forEveryCustomAgent() {
    assertTrue(createMode(CUSTOM_MODE_ID, null).allowsToolConfiguration());
    assertTrue(createMode(CUSTOM_MODE_ID, List.of()).allowsToolConfiguration());
    assertTrue(createMode(CUSTOM_MODE_ID, List.of("read_file")).allowsToolConfiguration());
  }

  @Test
  void testIsToolSelectionEditable_onlyWithoutExplicitToolList() {
    assertTrue(McpPreferencePage.isToolSelectionEditable(createMode(CUSTOM_MODE_ID, null)));
    assertFalse(McpPreferencePage.isToolSelectionEditable(createMode(CUSTOM_MODE_ID, List.of())));
    assertFalse(McpPreferencePage.isToolSelectionEditable(createMode(CUSTOM_MODE_ID, List.of("read_file"))));
  }

  private CustomChatMode createMode(String modeId, List<String> tools) {
    ConversationMode conversationMode = new ConversationMode();
    conversationMode.setId(modeId);
    conversationMode.setName("Test Agent");
    conversationMode.setCustomTools(tools);
    return new CustomChatMode(conversationMode);
  }
}
