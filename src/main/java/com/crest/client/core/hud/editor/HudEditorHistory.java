package com.crest.client.core.hud.editor;
import com.crest.client.core.CrestModules;

import com.crest.client.core.HudModule;

import java.util.*;

public class HudEditorHistory {
    
    private static final int MAX_HISTORY = 50;
    
    private final Deque<Action> undoStack = new ArrayDeque<>();
    private final Deque<Action> redoStack = new ArrayDeque<>();
    
    public void recordMove(HudModule module, int oldX, int oldY, int newX, int newY) {
        Action action = new MoveAction(module.getId(), oldX, oldY, newX, newY);
        addAction(action);
    }
    
    public void recordResize(HudModule module, int oldWidth, int oldHeight, int newWidth, int newHeight) {
        Action action = new ResizeAction(module.getId(), oldWidth, oldHeight, newWidth, newHeight);
        addAction(action);
    }
    
    public void recordVisibility(HudModule module, boolean oldVisible, boolean newVisible) {
        Action action = new VisibilityAction(module.getId(), oldVisible, newVisible);
        addAction(action);
    }
    
    public void recordSettings(HudModule module, Map<String, Object> oldSettings, Map<String, Object> newSettings) {
        Action action = new SettingsAction(module.getId(), oldSettings, newSettings);
        addAction(action);
    }
    
    private void addAction(Action action) {
        undoStack.push(action);
        redoStack.clear();
        
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
    }
    
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }
    
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
    
    public void undo() {
        if (!canUndo()) return;
        
        Action action = undoStack.pop();
        action.undo();
        redoStack.push(action);
    }
    
    public void redo() {
        if (!canRedo()) return;
        
        Action action = redoStack.pop();
        action.redo();
        undoStack.push(action);
    }
    
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
    
    private interface Action {
        void undo();
        void redo();
    }
    
    private static class MoveAction implements Action {
        private final String moduleId;
        private final int oldX, oldY, newX, newY;
        
        MoveAction(String moduleId, int oldX, int oldY, int newX, int newY) {
            this.moduleId = moduleId;
            this.oldX = oldX;
            this.oldY = oldY;
            this.newX = newX;
            this.newY = newY;
        }
        
        @Override
        public void undo() {
            HudModule module = findModule(moduleId);
            if (module != null) module.setX(oldX); module.setY(oldY);
        }
        
        @Override
        public void redo() {
            HudModule module = findModule(moduleId);
            if (module != null) module.setX(newX); module.setY(newY);
        }
    }
    
    private static class ResizeAction implements Action {
        private final String moduleId;
        private final int oldWidth, oldHeight, newWidth, newHeight;
        
        ResizeAction(String moduleId, int oldWidth, int oldHeight, int newWidth, int newHeight) {
            this.moduleId = moduleId;
            this.oldWidth = oldWidth;
            this.oldHeight = oldHeight;
            this.newWidth = newWidth;
            this.newHeight = newHeight;
        }
        
        @Override
        public void undo() {
        }
        
        @Override
        public void redo() {
        }
    }
    
    private static class VisibilityAction implements Action {
        private final String moduleId;
        private final boolean oldVisible, newVisible;
        
        VisibilityAction(String moduleId, boolean oldVisible, boolean newVisible) {
            this.moduleId = moduleId;
            this.oldVisible = oldVisible;
            this.newVisible = newVisible;
        }
        
        @Override
        public void undo() {
            HudModule module = findModule(moduleId);
            if (module != null) {
                if (oldVisible) CrestModules.setEnabled(module.getId(), true);
                else CrestModules.setEnabled(module.getId(), true);
            }
        }
        
        @Override
        public void redo() {
            HudModule module = findModule(moduleId);
            if (module != null) {
                if (newVisible) CrestModules.setEnabled(module.getId(), true);
                else CrestModules.setEnabled(module.getId(), true);
            }
        }
    }
    
    private static class SettingsAction implements Action {
        private final String moduleId;
        private final Map<String, Object> oldSettings;
        private final Map<String, Object> newSettings;
        
        SettingsAction(String moduleId, Map<String, Object> oldSettings, Map<String, Object> newSettings) {
            this.moduleId = moduleId;
            this.oldSettings = new HashMap<>(oldSettings);
            this.newSettings = new HashMap<>(newSettings);
        }
        
        @Override
        public void undo() {
        }
        
        @Override
        public void redo() {
        }
    }
    
    private static HudModule findModule(String id) {
        return com.crest.client.core.CrestModules.getHudModules().stream()
            .filter(m -> m.getId().equals(id))
            .findFirst()
            .orElse(null);
    }
}
