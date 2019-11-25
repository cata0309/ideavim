/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2019 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.action;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.KeyStrokeAdapter;
import com.maddyhome.idea.vim.KeyHandler;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.command.CommandState;
import com.maddyhome.idea.vim.handler.EditorActionHandlerBase;
import com.maddyhome.idea.vim.helper.CommandStateHelper;
import com.maddyhome.idea.vim.helper.EditorDataContext;
import com.maddyhome.idea.vim.helper.EditorHelper;
import com.maddyhome.idea.vim.helper.StringHelper;
import com.maddyhome.idea.vim.key.ShortcutOwner;
import com.maddyhome.idea.vim.listener.IdeaSpecifics;
import com.maddyhome.idea.vim.option.ListOption;
import com.maddyhome.idea.vim.option.OptionsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;

import static java.awt.event.KeyEvent.*;

/**
 * Handles Vim keys that are treated as action shortcuts by the IDE.
 * <p>
 * These keys are not passed to {@link com.maddyhome.idea.vim.VimTypedActionHandler} and should be handled by actions.
 */
public class VimShortcutKeyAction extends AnAction implements DumbAware {
  @NotNull public static final Set<KeyStroke> VIM_ONLY_EDITOR_KEYS =
    ImmutableSet.<KeyStroke>builder().addAll(getKeyStrokes(VK_ENTER, 0)).addAll(getKeyStrokes(VK_ESCAPE, 0))
      .addAll(getKeyStrokes(VK_TAB, 0)).addAll(getKeyStrokes(VK_BACK_SPACE, 0, CTRL_MASK))
      .addAll(getKeyStrokes(VK_INSERT, 0)).addAll(getKeyStrokes(VK_DELETE, 0, CTRL_MASK))
      .addAll(getKeyStrokes(VK_UP, 0, CTRL_MASK, SHIFT_MASK)).addAll(getKeyStrokes(VK_DOWN, 0, CTRL_MASK, SHIFT_MASK))
      .addAll(getKeyStrokes(VK_LEFT, 0, CTRL_MASK, SHIFT_MASK, CTRL_MASK | SHIFT_MASK))
      .addAll(getKeyStrokes(VK_RIGHT, 0, CTRL_MASK, SHIFT_MASK, CTRL_MASK | SHIFT_MASK))
      .addAll(getKeyStrokes(VK_HOME, 0, CTRL_MASK, SHIFT_MASK, CTRL_MASK | SHIFT_MASK))
      .addAll(getKeyStrokes(VK_END, 0, CTRL_MASK, SHIFT_MASK, CTRL_MASK | SHIFT_MASK))
      .addAll(getKeyStrokes(VK_PAGE_UP, 0, SHIFT_MASK, CTRL_MASK | SHIFT_MASK))
      .addAll(getKeyStrokes(VK_PAGE_DOWN, 0, SHIFT_MASK, CTRL_MASK | SHIFT_MASK)).build();
  private static final String ACTION_ID = "VimShortcutKeyAction";
  @NotNull private static final Set<KeyStroke> NON_FILE_EDITOR_KEYS =
    ImmutableSet.<KeyStroke>builder().addAll(getKeyStrokes(VK_ENTER, 0)).addAll(getKeyStrokes(VK_ESCAPE, 0))
      .addAll(getKeyStrokes(VK_TAB, 0)).addAll(getKeyStrokes(VK_UP, 0)).addAll(getKeyStrokes(VK_DOWN, 0)).build();

  private static final Logger ourLogger = Logger.getInstance(VimShortcutKeyAction.class.getName());
  private static AnAction ourInstance = null;

  @NotNull
  public static AnAction getInstance() {
    if (ourInstance == null) {
      final AnAction originalAction = ActionManager.getInstance().getAction(ACTION_ID);
      ourInstance = EmptyAction.wrap(originalAction);
    }
    return ourInstance;
  }

  @NotNull
  private static List<KeyStroke> getKeyStrokes(int keyCode, @NotNull int... modifiers) {
    final List<KeyStroke> keyStrokes = new ArrayList<>();
    for (int modifier : modifiers) {
      keyStrokes.add(KeyStroke.getKeyStroke(keyCode, modifier));
    }
    return keyStrokes;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Editor editor = getEditor(e);
    final KeyStroke keyStroke = getKeyStroke(e);
    if (editor != null && keyStroke != null) {
      final ShortcutOwner owner = VimPlugin.getKey().getSavedShortcutConflicts().get(keyStroke);
      if (owner == ShortcutOwner.UNDEFINED) {
        VimPlugin.getNotifications(editor.getProject()).notifyAboutShortcutConflict(keyStroke);
      }
      // Should we use HelperKt.getTopLevelEditor(editor) here, as we did in former EditorKeyHandler?
      try {
        KeyHandler.getInstance().handleKey(editor, keyStroke, new EditorDataContext(editor));
      }
      catch (ProcessCanceledException ignored) {
        // Control-flow exceptions (like ProcessCanceledException) should never be logged
        // See {@link com.intellij.openapi.diagnostic.Logger.checkException}
      }
      catch (Throwable throwable) {
        ourLogger.error(throwable);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  private boolean isEnabled(@NotNull AnActionEvent e) {
    if (!VimPlugin.isEnabled()) return false;

    final Editor editor = getEditor(e);
    final KeyStroke keyStroke = getKeyStroke(e);
    if (editor != null && keyStroke != null) {
      // Workaround for smart step into
      final Key<?> SMART_STEP_INPLACE_DATA = Key.findKeyByName("SMART_STEP_INPLACE_DATA");
      if (SMART_STEP_INPLACE_DATA != null && editor.getUserData(SMART_STEP_INPLACE_DATA) != null) return false;
      if (IdeaSpecifics.INSTANCE.aceJumpActive()) return false;

      final int keyCode = keyStroke.getKeyCode();
      if (LookupManager.getActiveLookup(editor) != null) {
        return isEnabledForLookup(keyStroke);
      }
      if (keyCode == VK_ESCAPE) {
        return isEnabledForEscape(editor);
      }
      if (CommandStateHelper.inInsertMode(editor)) {
        // XXX: <Tab> won't be recorded in macros
        if (keyCode == VK_TAB) {
          VimPlugin.getChange().tabAction = true;
          return false;
        }
        // Debug watch, Python console, etc.
        if (NON_FILE_EDITOR_KEYS.contains(keyStroke) && !EditorHelper.isFileEditor(editor)) {
          return false;
        }
      }
      if (VIM_ONLY_EDITOR_KEYS.contains(keyStroke)) {
        return true;
      }
      final Map<KeyStroke, ShortcutOwner> savedShortcutConflicts = VimPlugin.getKey().getSavedShortcutConflicts();
      final ShortcutOwner owner = savedShortcutConflicts.get(keyStroke);
      if (owner == ShortcutOwner.VIM) {
        return true;
      }
      else if (owner == ShortcutOwner.IDE) {
        return !isShortcutConflict(keyStroke);
      }
      else {
        if (isShortcutConflict(keyStroke)) {
          savedShortcutConflicts.put(keyStroke, ShortcutOwner.UNDEFINED);
        }
        return true;
      }
    }
    return false;
  }

  private boolean isEnabledForEscape(@NotNull Editor editor) {
    final CommandState.Mode mode = CommandState.getInstance(editor).getMode();
    return isPrimaryEditor(editor) || (EditorHelper.isFileEditor(editor) && mode != CommandState.Mode.COMMAND);
  }

  /**
   * Checks if the editor is a primary editor in the main editing area.
   */
  private boolean isPrimaryEditor(@NotNull Editor editor) {
    final Project project = editor.getProject();
    if (project == null) return false;
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    if (fileEditorManager == null) return false;
    return Arrays.stream(fileEditorManager.getAllEditors())
      .anyMatch(fileEditor -> editor.equals(EditorUtil.getEditorEx(fileEditor)));
  }

  private boolean isEnabledForLookup(@NotNull KeyStroke keyStroke) {
    final Set<List<KeyStroke>> allowedKeys = EditorActionHandlerBase.parseKeysSet(
      // Escape
      "<C-[>", "<C-C>", "<Esc>",
      // Backspace
      "<BS>",
      // One command
      "<C-O>",
      // Lookup up
      "<C-P>",
      // Lookup down
      "<C-N>");

    for (List<KeyStroke> keys : allowedKeys) {
      // XXX: Currently we cannot handle <C-\><C-N> because of the importance of <C-N> for the IDE on Linux
      if (keyStroke.equals(keys.get(0))) {
        return true;
      }
    }

    // We allow users to set custom keys that will work with lookup in case devs forgot something
    final ListOption popupActions = OptionsManager.INSTANCE.getLookupKeys();
    final List<String> values = popupActions.values();
    if (values == null) return false;
    for (String value : values) {
      final List<KeyStroke> keys = StringHelper.parseKeys(value);
      if (keys.size() >= 1 && keyStroke.equals(keys.get(0))) {
        return true;
      }
    }

    return false;
  }

  private boolean isShortcutConflict(@NotNull KeyStroke keyStroke) {
    return !VimPlugin.getKey().getKeymapConflicts(keyStroke).isEmpty();
  }

  /**
   * getDefaultKeyStroke is needed for NEO layout keyboard VIM-987
   * but we should cache the value because on the second call (isEnabled -> actionPerformed)
   * the event is already consumed
   */
  @NotNull private Pair<KeyEvent, KeyStroke> keyStrokeCache = new Pair<>(null, null);

  @Nullable
  private KeyStroke getKeyStroke(@NotNull AnActionEvent e) {
    final InputEvent inputEvent = e.getInputEvent();
    if (inputEvent instanceof KeyEvent) {
      final KeyEvent keyEvent = (KeyEvent)inputEvent;
      final KeyStroke defaultKeyStroke = KeyStrokeAdapter.getDefaultKeyStroke(keyEvent);
      Pair<KeyEvent, KeyStroke> strokeCache = this.keyStrokeCache;
      if (defaultKeyStroke != null) {
        this.keyStrokeCache = new Pair<>(keyEvent, defaultKeyStroke);
        return defaultKeyStroke;
      }
      else if (strokeCache.first == keyEvent) {
        this.keyStrokeCache = new Pair<>(null, null);
        return strokeCache.second;
      }
      return KeyStroke.getKeyStrokeForEvent(keyEvent);
    }
    return null;
  }

  @Nullable
  private Editor getEditor(@NotNull AnActionEvent e) {
    return e.getData(PlatformDataKeys.EDITOR);
  }
}