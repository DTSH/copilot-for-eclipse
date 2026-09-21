// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.preferences;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.microsoft.copilot.eclipse.core.chat.CustomChatMode;
import com.microsoft.copilot.eclipse.core.lsp.mcp.McpServerToolsCollection;
import com.microsoft.copilot.eclipse.core.lsp.mcp.McpToolInformation;
import com.microsoft.copilot.eclipse.core.lsp.protocol.ConversationMode;
import com.microsoft.copilot.eclipse.core.lsp.protocol.LanguageModelToolInformation;

class CustomAgentToolStatusResolverTest {
  private static final String BUILT_IN_TOOLS = "Built-in Tools";
  private static final String CUSTOM_MODE_ID = "file:///workspace/.github/agents/test.agent.md";

  private final CustomAgentToolStatusResolver resolver = new CustomAgentToolStatusResolver(BUILT_IN_TOOLS);

  @Test
  void testMissingToolListInitializesAllToolsWithoutAgentModeInheritance() {
    Map<String, Map<String, Map<String, Boolean>>> existing = new LinkedHashMap<>();
    existing.put(CustomAgentToolStatusResolver.AGENT_MODE_ID,
        Map.of(BUILT_IN_TOOLS, Map.of("java_debugger", false)));

    Map<String, Map<String, Map<String, Boolean>>> result = resolver.resolveCustomAgentToolStatus(
        List.of(customMode(CUSTOM_MODE_ID, null)), existing,
        List.of(tool("java_debugger")), List.of(server("company", "search")));

    Map<String, Map<String, Boolean>> customStatus = result.get(CUSTOM_MODE_ID);
    assertTrue(customStatus.get(BUILT_IN_TOOLS).get("java_debugger"));
    assertTrue(customStatus.get("company").get("search"));
  }

  @Test
  void testMissingToolListKeepsExistingCustomPreferences() {
    Map<String, Map<String, Map<String, Boolean>>> existing = new LinkedHashMap<>();
    existing.put(CUSTOM_MODE_ID, Map.of(BUILT_IN_TOOLS, Map.of("java_debugger", false)));

    Map<String, Map<String, Map<String, Boolean>>> result = resolver.resolveCustomAgentToolStatus(
        List.of(customMode(CUSTOM_MODE_ID, null)), existing,
        List.of(tool("java_debugger"), tool("edit_file")), List.of(server("company", "search")));

    Map<String, Map<String, Boolean>> customStatus = result.get(CUSTOM_MODE_ID);
    assertFalse(customStatus.get(BUILT_IN_TOOLS).get("java_debugger"));
    assertNull(customStatus.get(BUILT_IN_TOOLS).get("edit_file"));
    assertNull(customStatus.get("company"));
  }

  @Test
  void testExplicitToolListEnablesOnlyListedToolsAndSplitsAtLastSlash() {
    CustomChatMode mode = customMode(CUSTOM_MODE_ID, List.of("java_debugger", "company/platform/search",
        " ", "company/", "/broken"));

    Map<String, Map<String, Map<String, Boolean>>> result = resolver.resolveCustomAgentToolStatus(List.of(mode),
        new LinkedHashMap<>(), List.of(tool("java_debugger"), tool("edit_file")),
        List.of(server("company/platform", "search", "list")));

    Map<String, Map<String, Boolean>> customStatus = result.get(CUSTOM_MODE_ID);
    assertTrue(customStatus.get(BUILT_IN_TOOLS).get("java_debugger"));
    assertFalse(customStatus.get(BUILT_IN_TOOLS).get("edit_file"));
    assertTrue(customStatus.get("company/platform").get("search"));
    assertFalse(customStatus.get("company/platform").get("list"));
    assertFalse(customStatus.containsKey("company"));
  }

  @Test
  void testExplicitEmptyToolListDisablesAllKnownTools() {
    CustomChatMode mode = customMode(CUSTOM_MODE_ID, List.of());

    Map<String, Map<String, Boolean>> customStatus = resolver.resolveModeToolStatus(mode, null,
        List.of(tool("java_debugger")), List.of(server("company", "search")));

    assertFalse(customStatus.get(BUILT_IN_TOOLS).get("java_debugger"));
    assertFalse(customStatus.get("company").get("search"));
  }

  @Test
  void testMissingToolListWaitsForCompleteInventory() {
    Map<String, Map<String, Boolean>> customStatus = resolver.resolveModeToolStatus(
        customMode(CUSTOM_MODE_ID, null), null, null, List.of(server("company", "search")));

    assertNull(customStatus);
  }

  @Test
  void testCheckboxEditabilityDependsOnlyOnExplicitToolsEntry() {
    assertTrue(resolver.isToolStatusEditable(customMode(CUSTOM_MODE_ID, null)));
    assertFalse(resolver.isToolStatusEditable(customMode(CUSTOM_MODE_ID, List.of())));
  }

  @Test
  void testMultipleCustomAgentsRemainIsolated() {
    String otherModeId = "file:///workspace/.github/agents/other.agent.md";
    Map<String, Map<String, Map<String, Boolean>>> existing = new LinkedHashMap<>();
    existing.put(CustomAgentToolStatusResolver.AGENT_MODE_ID,
        Map.of(BUILT_IN_TOOLS, Map.of("java_debugger", true)));
    existing.put(otherModeId, Map.of(BUILT_IN_TOOLS, Map.of("java_debugger", false)));

    Map<String, Map<String, Map<String, Boolean>>> result = resolver.resolveCustomAgentToolStatus(
        List.of(customMode(CUSTOM_MODE_ID, List.of("java_debugger")), customMode(otherModeId, null)), existing,
        List.of(tool("java_debugger"), tool("edit_file")), List.of());

    Map<String, Map<String, Boolean>> explicitStatus = result.get(CUSTOM_MODE_ID);
    Map<String, Map<String, Boolean>> existingStatus = result.get(otherModeId);
    assertTrue(explicitStatus.get(BUILT_IN_TOOLS).get("java_debugger"));
    assertFalse(explicitStatus.get(BUILT_IN_TOOLS).get("edit_file"));
    assertFalse(existingStatus.get(BUILT_IN_TOOLS).get("java_debugger"));
    assertNull(existingStatus.get(BUILT_IN_TOOLS).get("edit_file"));
  }

  @Test
  void testSimpleConstructorRepresentsMissingToolsEntry() {
    CustomChatMode mode = new CustomChatMode(CUSTOM_MODE_ID, "Test Agent", "Test description");

    Map<String, Map<String, Boolean>> customStatus = resolver.resolveModeToolStatus(mode, null,
        List.of(tool("java_debugger")), List.of(server("company", "search")));

    assertTrue(mode.allowsToolConfiguration());
    assertTrue(resolver.isToolStatusEditable(mode));
    assertTrue(customStatus.get(BUILT_IN_TOOLS).get("java_debugger"));
    assertTrue(customStatus.get("company").get("search"));
  }

  private static CustomChatMode customMode(String id, List<String> tools) {
    ConversationMode mode = new ConversationMode();
    mode.setId(id);
    mode.setName("Test Agent");
    mode.setDescription("Test description");
    mode.setCustomTools(tools);
    return new CustomChatMode(mode);
  }

  private static LanguageModelToolInformation tool(String name) {
    LanguageModelToolInformation tool = new LanguageModelToolInformation();
    tool.setName(name);
    return tool;
  }

  private static McpServerToolsCollection server(String name, String... toolNames) {
    McpServerToolsCollection server = new McpServerToolsCollection();
    server.setName(name);
    server.setTools(List.of(toolNames).stream().map(CustomAgentToolStatusResolverTest::mcpTool).toList());
    return server;
  }

  private static McpToolInformation mcpTool(String name) {
    McpToolInformation tool = new McpToolInformation();
    tool.setName(name);
    return tool;
  }
}
