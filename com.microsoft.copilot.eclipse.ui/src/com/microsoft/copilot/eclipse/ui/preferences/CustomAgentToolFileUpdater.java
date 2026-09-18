// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.microsoft.copilot.eclipse.ui.preferences;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.microsoft.copilot.eclipse.core.CopilotCore;

/**
 * Aktualisiert die Tool-Konfiguration in {@code .agent.md}-Dateien.
 */
final class CustomAgentToolFileUpdater {
  private static final String FRONTMATTER_SEPARATOR = "---";
  private static final String UTF_8_BOM = "\uFEFF";

  private CustomAgentToolFileUpdater() {
  }

  static void updateTools(String modeId, List<String> tools, IProgressMonitor monitor) throws IOException {
    URI uri = URI.create(modeId);
    Path path = Paths.get(uri);
    if (!Files.exists(path)) {
      throw new IOException("Custom agent file does not exist: " + path);
    }

    Charset charset = resolveCharset(path);
    String content = Files.readString(path, charset);
    String updatedContent = updateToolsInContent(content, tools);

    if (!content.equals(updatedContent)) {
      Files.writeString(path, updatedContent, charset);
    }

    refreshWorkspaceFile(path, monitor == null ? new NullProgressMonitor() : monitor);
  }

  static List<String> toToolSpecifications(Map<String, Map<String, Boolean>> serverToolStatus,
      String builtInServerName) {
    TreeSet<String> toolSpecifications = new TreeSet<>();
    if (serverToolStatus == null) {
      return new ArrayList<>();
    }

    for (Map.Entry<String, Map<String, Boolean>> serverEntry : serverToolStatus.entrySet()) {
      String serverName = serverEntry.getKey();
      Map<String, Boolean> tools = serverEntry.getValue();
      if (StringUtils.isBlank(serverName) || tools == null) {
        continue;
      }

      for (Map.Entry<String, Boolean> toolEntry : tools.entrySet()) {
        if (!Boolean.TRUE.equals(toolEntry.getValue()) || StringUtils.isBlank(toolEntry.getKey())) {
          continue;
        }

        if (builtInServerName.equals(serverName)) {
          toolSpecifications.add(toolEntry.getKey());
        } else {
          toolSpecifications.add(serverName + "/" + toolEntry.getKey());
        }
      }
    }
    return new ArrayList<>(toolSpecifications);
  }

  static String updateToolsInContent(String content, List<String> tools) {
    String safeContent = content == null ? StringUtils.EMPTY : content;
    String lineSeparator = detectLineSeparator(safeContent);
    boolean hasBom = safeContent.startsWith(UTF_8_BOM);
    String contentWithoutBom = hasBom ? safeContent.substring(UTF_8_BOM.length()) : safeContent;
    List<String> lines = new ArrayList<>(Arrays.asList(contentWithoutBom.split("\\R", -1)));
    List<String> toolsBlock = createToolsBlock(tools);

    if (hasFrontmatter(lines)) {
      int closingIndex = findClosingFrontmatterIndex(lines);
      if (closingIndex > 0) {
        upsertToolsBlock(lines, closingIndex, toolsBlock);
        return (hasBom ? UTF_8_BOM : StringUtils.EMPTY) + String.join(lineSeparator, lines);
      }
    }

    List<String> updatedLines = new ArrayList<>();
    updatedLines.add(FRONTMATTER_SEPARATOR);
    updatedLines.addAll(toolsBlock);
    updatedLines.add(FRONTMATTER_SEPARATOR);
    updatedLines.addAll(lines);
    return (hasBom ? UTF_8_BOM : StringUtils.EMPTY) + String.join(lineSeparator, updatedLines);
  }

  private static Charset resolveCharset(Path path) {
    IFile workspaceFile = findWorkspaceFile(path);
    if (workspaceFile == null) {
      return StandardCharsets.UTF_8;
    }

    try {
      String charsetName = workspaceFile.getCharset(true);
      if (StringUtils.isNotBlank(charsetName)) {
        return Charset.forName(charsetName);
      }
    } catch (CoreException | IllegalArgumentException e) {
      CopilotCore.LOGGER.error("Failed to determine charset for " + path, e);
    }
    return StandardCharsets.UTF_8;
  }

  private static IFile findWorkspaceFile(Path path) {
    IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(path.toUri());
    return files.length > 0 ? files[0] : null;
  }

  private static void refreshWorkspaceFile(Path path, IProgressMonitor monitor) {
    IFile workspaceFile = findWorkspaceFile(path);
    if (workspaceFile == null || !workspaceFile.exists()) {
      return;
    }

    try {
      workspaceFile.refreshLocal(IResource.DEPTH_ZERO, monitor);
    } catch (CoreException e) {
      CopilotCore.LOGGER.error("Failed to refresh " + workspaceFile.getName(), e);
    }
  }

  private static String detectLineSeparator(String content) {
    return content.contains("\r\n") ? "\r\n" : "\n";
  }

  private static boolean hasFrontmatter(List<String> lines) {
    return !lines.isEmpty() && FRONTMATTER_SEPARATOR.equals(lines.get(0).trim());
  }

  private static int findClosingFrontmatterIndex(List<String> lines) {
    for (int i = 1; i < lines.size(); i++) {
      if (FRONTMATTER_SEPARATOR.equals(lines.get(i).trim())) {
        return i;
      }
    }
    return -1;
  }

  private static void upsertToolsBlock(List<String> lines, int closingIndex, List<String> toolsBlock) {
    int toolsIndex = findToolsKeyIndex(lines, closingIndex);
    if (toolsIndex < 0) {
      lines.addAll(closingIndex, toolsBlock);
      return;
    }

    int toolsBlockEnd = findToolsBlockEnd(lines, toolsIndex, closingIndex);
    lines.subList(toolsIndex, toolsBlockEnd).clear();
    lines.addAll(toolsIndex, toolsBlock);
  }

  private static int findToolsKeyIndex(List<String> lines, int closingIndex) {
    for (int i = 1; i < closingIndex; i++) {
      if (isToolsKey(lines.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isToolsKey(String line) {
    String trimmedLine = line.trim();
    return trimmedLine.startsWith("tools:") || trimmedLine.matches("tools\\s*:.*");
  }

  private static int findToolsBlockEnd(List<String> lines, int toolsIndex, int closingIndex) {
    int toolsIndent = countLeadingWhitespace(lines.get(toolsIndex));
    for (int i = toolsIndex + 1; i < closingIndex; i++) {
      String line = lines.get(i);
      String trimmedLine = line.trim();
      if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
        continue;
      }

      int indent = countLeadingWhitespace(line);
      if (indent <= toolsIndent && isYamlKey(trimmedLine)) {
        return i;
      }
    }
    return closingIndex;
  }

  private static boolean isYamlKey(String trimmedLine) {
    return !trimmedLine.startsWith("-") && trimmedLine.indexOf(':') > 0;
  }

  private static int countLeadingWhitespace(String line) {
    int count = 0;
    while (count < line.length() && Character.isWhitespace(line.charAt(count))) {
      count++;
    }
    return count;
  }

  private static List<String> createToolsBlock(List<String> tools) {
    TreeSet<String> sortedTools = new TreeSet<>();
    if (tools != null) {
      tools.stream().filter(StringUtils::isNotBlank).forEach(sortedTools::add);
    }

    if (sortedTools.isEmpty()) {
      return List.of("tools: []");
    }

    List<String> lines = new ArrayList<>();
    lines.add("tools:");
    sortedTools.forEach(tool -> lines.add("  - " + toQuotedYamlString(tool)));
    return lines;
  }

  private static String toQuotedYamlString(String value) {
    return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
  }
}