(ns app.main)

(def electron       (js/require "electron"))
(def app            (.-app electron))
(def BrowserWindow  (.-BrowserWindow electron))
(def globalShortcut (.-globalShortcut electron))
(def ipcMain        (.-ipcMain electron))
(def clipboard      (.-clipboard electron))
(def Tray           (.-Tray electron))
(def Menu           (.-Menu electron))

(goog-define dev? false)

(defn resource [file]
  (if dev?
    (str js/__dirname "/../../" file)
    (str js/__dirname "/" file)))

(defn resource-with-header [file]
  (str "file://" (resource file)))

(defn load-page [window]
  (.loadURL window (resource-with-header "index.html")))

(defn registr-global-shortcut [shortcut func]
  (.register globalShortcut shortcut func))

(defn unregistr-global-shortcut [shortcut]
  (.unregister globalShortcut shortcut))

(defn unregistr-all-global-shortcut []
  (.unregisterAll globalShortcut))

(defn global-shortcutis-is-registered? [shortcut]
  (.isRegistered globalShortcut shortcut))

(defn read-selection-text []
  (.readText clipboard "selection"))

(defn get-cursor-point [screen]
  (.getCursorScreenPoint screen))

(defn build-menu [template]
  (.buildFromTemplate Menu (clj->js template)))

(def main-window (atom nil))

(defn mk-window [w h frame? show? & {:keys [skip-taskbar min-width min-height
                                            always-on-top]}]
  (BrowserWindow. (clj->js {:width w :height h :frame frame? :show show?
                            :minWidth min-width :minHeight min-height
                            :skipTaskbar skip-taskbar
                            :alwaysOnTop always-on-top})))

(enable-console-print!)

(defn init-browser []
  (reset! main-window (mk-window 300 200 false false
                                 :min-width 250 :min-height 150
                                 :always-on-top true
                                 :skip-taskbar true))
  (load-page @main-window)
  (if dev? (.openDevTools @main-window))

  (registr-global-shortcut "Shift+CommandOrControl+X"
                           #(if (.isVisible @main-window)
                              (.hide @main-window)
                              (let [screen (.-screen electron)
                                    cursor-point (get-cursor-point screen)]
                                (.setPosition @main-window
                                              (.-x cursor-point) (.-y cursor-point))
                                (.showInactive @main-window)
                                (.send (.-webContents @main-window)
                                       "translate" (read-selection-text)))))

  (let [tary (Tray. (resource "icon.png"))
        menu (build-menu [{:label "Exit"
                           :click #(.quit app)}])]
    (.setToolTip tary "youdao-electron")
    (.setContextMenu tary menu))

  (.on @main-window "blur" #(.hide @main-window))

  (.on @main-window "closed" #(reset! main-window nil)))

(defn init []
  (.on app "window-all-closed" #(when-not (= js/process.platform "darwin") (.quit app)))
  (.on app "will-quit" #(unregistr-all-global-shortcut))
  (.on app "ready" init-browser)
  (set! *main-cli-fn* (fn [] nil)))
