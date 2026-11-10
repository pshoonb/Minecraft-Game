import os
import re
import json
import shutil
import hashlib
import zipfile
import subprocess
import sys
import urllib.request
import tkinter as tk

from pathlib import Path
from datetime import datetime
from tkinter import filedialog

# =====================================================
# RPG CORE ONE CLICK INSTALLER
# Windows / TLauncher
# Minecraft 1.21.1
# NeoForge 21.1.265
# =====================================================

NEOFORGE_VERSION = "21.1.265"
MOD_JAR_PREFIX = "rpgcore-"

# Public GitHub Releases channel. No GitHub token is needed by players.
GITHUB_RELEASE_API = (
    "https://api.github.com/repos/pshoonb/Minecraft-Game/releases/latest"
)

# Installer source used for the optional manual self-update check.
INSTALLER_UPDATE_URL = (
    "https://raw.githubusercontent.com/pshoonb/Minecraft-Game/main/"
    "RPG_Core_%E4%B8%80%E9%94%AE%E5%AE%89%E8%A3%85_%E8%87%AA%E5%8A%A8%E6%9B%B4%E6%96%B0%E7%89%88.py"
)

INSTALLER_NAME = f"neoforge-{NEOFORGE_VERSION}-installer.jar"
STATE_NAME = ".rpgcore_installer_state.json"

JAVA_API = (
    "https://api.adoptium.net/v3/assets/latest/21/hotspot"
    "?architecture=x64&image_type=jre&os=windows&vendor=eclipse"
)

APP_NAME = "RPG Core Installer"
INSTALLER_EXE_NAME = "RPG-Core-Installer.exe"
INSTALLER_UPDATE_MAX_BYTES = 100 * 1024 * 1024
IS_FROZEN = bool(getattr(sys, "frozen", False))
BASE_DIR = (
    Path(sys.executable).resolve().parent
    if IS_FROZEN
    else Path(__file__).resolve().parent
)

# Keep diagnostic output available when the Windows EXE is built without a console.
_FROZEN_LOG_HANDLE = None
if IS_FROZEN and (sys.stdout is None or sys.stderr is None):
    try:
        _log_dir = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "RPGCoreInstaller"
        _log_dir.mkdir(parents=True, exist_ok=True)
        _FROZEN_LOG_HANDLE = open(_log_dir / "installer.log", "a", encoding="utf-8")
        if sys.stdout is None:
            sys.stdout = _FROZEN_LOG_HANDLE
        if sys.stderr is None:
            sys.stderr = _FROZEN_LOG_HANDLE
    except OSError:
        _FROZEN_LOG_HANDLE = open(os.devnull, "w", encoding="utf-8")
        if sys.stdout is None:
            sys.stdout = _FROZEN_LOG_HANDLE
        if sys.stderr is None:
            sys.stderr = _FROZEN_LOG_HANDLE

APPDATA = Path(
    os.environ.get(
        "APPDATA",
        Path.home() / "AppData" / "Roaming"
    )
)

# 默认游戏目录；用户可以在启动时改选其他目录。
DEFAULT_GAME_DIR = APPDATA / ".minecraft"
GAME_DIR = DEFAULT_GAME_DIR

# Java 与模组下载缓存（与 Minecraft 游戏目录无关）。
LOCAL_APP_DATA = Path(
    os.environ.get("LOCALAPPDATA", str(Path.home()))
)
INSTALLER_CACHE_DIR = LOCAL_APP_DATA / "RPGCoreInstaller"
JAVA_DIR = INSTALLER_CACHE_DIR / "Java21"
MOD_CACHE_DIR = INSTALLER_CACHE_DIR / "ModCache"


# =====================================================
# GAME DIRECTORY PICKER
# =====================================================

def choose_game_directory(default_dir: Path):
    """Show the game directory picker; return None if cancelled."""

    window = tk.Tk()
    window.title("Select Minecraft Game Directory")
    window.resizable(False, False)

    width = 680
    height = 190
    screen_width = window.winfo_screenwidth()
    screen_height = window.winfo_screenheight()
    x = max(0, (screen_width - width) // 2)
    y = max(0, (screen_height - height) // 2)
    window.geometry(f"{width}x{height}+{x}+{y}")

    path_var = tk.StringVar(master=window, value=str(default_dir))
    result = {"path": None}

    tk.Label(
        window,
        text="Please select your Minecraft game directory:",
        font=("Segoe UI", 10, "bold")
    ).pack(anchor="w", padx=16, pady=(15, 5))

    tk.Label(
        window,
        text="The default is .minecraft. If TLauncher uses a separate game directory, click “Browse...” to select it.",
        fg="gray",
        wraplength=640,
        justify="left"
    ).pack(anchor="w", padx=16)

    path_frame = tk.Frame(window)
    path_frame.pack(fill="x", padx=16, pady=12)

    entry = tk.Entry(
        path_frame,
        textvariable=path_var,
        width=76
    )
    entry.pack(side="left", fill="x", expand=True)

    def browse():
        current = Path(path_var.get().strip().strip('"')).expanduser()

        if current.is_dir():
            initial_dir = current
        elif current.parent.is_dir():
            initial_dir = current.parent
        elif default_dir.parent.is_dir():
            initial_dir = default_dir.parent
        else:
            initial_dir = Path.home()

        selected = filedialog.askdirectory(
            parent=window,
            title="Select Minecraft Game Directory",
            initialdir=str(initial_dir),
            mustexist=True
        )

        if selected:
            path_var.set(selected)

    def confirm():
        raw_path = path_var.get().strip().strip('"')

        if not raw_path:
            show_error(
                "The game directory cannot be empty.",
                parent=window
            )
            return

        selected = Path(raw_path).expanduser()

        if selected.exists() and not selected.is_dir():
            show_error(
                "The selected path is not a folder. Please choose another path.",
                parent=window
            )
            return

        if not selected.exists():
            create_it = ask_yes_no(
                "This folder does not exist yet:\n\n"
                f"{selected}\n\n"
                "This folder will be created during installation. Do you want to use this path?",
                parent=window
            )
            if not create_it:
                return

        result["path"] = selected.resolve()
        window.destroy()

    def cancel():
        window.destroy()

    tk.Button(
        path_frame,
        text="Browse...",
        command=browse,
        width=10
    ).pack(side="left", padx=(8, 0))

    button_frame = tk.Frame(window)
    button_frame.pack(pady=(0, 14))

    tk.Button(
        button_frame,
        text="Use This Folder",
        command=confirm,
        width=16
    ).pack(side="left", padx=5)

    tk.Button(
        button_frame,
        text="Cancel",
        command=cancel,
        width=10
    ).pack(side="left", padx=5)

    window.protocol("WM_DELETE_WINDOW", cancel)
    window.mainloop()

    return result["path"]


def _show_dialog(text, buttons, default_value=False, parent=None):
    """Display a modal dialog with application-controlled English buttons."""

    dialog = tk.Tk() if parent is None else tk.Toplevel(parent)
    dialog.title(APP_NAME)
    dialog.resizable(False, False)

    result = {"value": default_value}

    message_label = tk.Label(
        dialog,
        text=str(text),
        font=("Segoe UI", 10),
        justify="left",
        anchor="w",
        wraplength=560
    )
    message_label.pack(fill="both", expand=True, padx=20, pady=(20, 12))

    button_frame = tk.Frame(dialog)
    button_frame.pack(fill="x", padx=20, pady=(0, 16))

    def finish(value):
        result["value"] = value
        try:
            dialog.grab_release()
        except tk.TclError:
            pass
        dialog.destroy()

    # Reverse packing keeps the options in their natural left-to-right order.
    for label, value in reversed(buttons):
        button = tk.Button(
            button_frame,
            text=label,
            width=12,
            command=lambda selected=value: finish(selected),
            default="active" if value == default_value else "normal"
        )
        button.pack(side="right", padx=(8, 0))
        if value == default_value:
            default_button = button

    dialog.protocol("WM_DELETE_WINDOW", lambda: finish(default_value))
    dialog.bind("<Escape>", lambda event: finish(default_value))
    dialog.bind("<Return>", lambda event: finish(default_value))

    if parent is not None:
        dialog.transient(parent)

    dialog.update_idletasks()
    width = max(380, min(640, message_label.winfo_reqwidth() + 40))
    height = message_label.winfo_reqheight() + 88

    if parent is not None:
        parent.update_idletasks()
        x = parent.winfo_rootx() + max(0, (parent.winfo_width() - width) // 2)
        y = parent.winfo_rooty() + max(0, (parent.winfo_height() - height) // 2)
    else:
        screen_width = dialog.winfo_screenwidth()
        screen_height = dialog.winfo_screenheight()
        x = max(0, (screen_width - width) // 2)
        y = max(0, (screen_height - height) // 2)

    dialog.geometry(f"{width}x{height}+{x}+{y}")
    dialog.grab_set()

    try:
        default_button.focus_set()
    except UnboundLocalError:
        pass

    if parent is None:
        dialog.mainloop()
    else:
        dialog.wait_window()

    return result["value"]


def show_error(text, parent=None):
    _show_dialog(text, [("OK", True)], default_value=True, parent=parent)


def show_warning(text, parent=None):
    _show_dialog(text, [("OK", True)], default_value=True, parent=parent)


def show_info(text, parent=None):
    _show_dialog(text, [("OK", True)], default_value=True, parent=parent)


def ask_yes_no(text, parent=None):
    return bool(
        _show_dialog(
            text,
            [("Yes", True), ("No", False)],
            default_value=False,
            parent=parent
        )
    )


def ask_ok_cancel(text, parent=None):
    return bool(
        _show_dialog(
            text,
            [("OK", True), ("Cancel", False)],
            default_value=False,
            parent=parent
        )
    )


def get_embedded_installer_version():
    """Read the build tag embedded by the Windows packaging workflow."""

    roots = []
    if IS_FROZEN:
        roots.append(Path(getattr(sys, "_MEIPASS", BASE_DIR)))
    roots.append(BASE_DIR)

    for root in roots:
        version_file = root / "installer-version.txt"
        try:
            version = version_file.read_text(encoding="utf-8").strip()
            if version:
                return version
        except OSError:
            continue

    return "Unversioned / local build"


def get_latest_installer_release():
    """Get the latest stable GitHub Release and its verified installer asset."""

    with github_request(GITHUB_RELEASE_API, timeout=30) as response:
        release = json.loads(response.read().decode("utf-8"))

    if (
        not isinstance(release, dict)
        or release.get("draft")
        or release.get("prerelease")
    ):
        raise RuntimeError("GitHub did not return a stable public Release.")

    tag = release.get("tag_name")
    assets = release.get("assets", [])
    if not tag or not isinstance(assets, list):
        raise RuntimeError("The latest GitHub Release metadata is incomplete.")

    installer_assets = [
        asset for asset in assets
        if asset.get("name") == INSTALLER_EXE_NAME
    ]

    # A new Mod Release is created before its Windows EXE packaging job uploads
    # the installer. During that short interval, simply skip the update check.
    if not installer_assets:
        print("The latest Release does not have its installer asset yet; skipping self-update.", flush=True)
        return None

    if len(installer_assets) != 1:
        raise RuntimeError("The latest Release contains multiple installer EXE assets.")

    asset = installer_assets[0]
    download_url = asset.get("browser_download_url")
    if (
        not download_url
        or not download_url.startswith(
            "https://github.com/pshoonb/Minecraft-Game/releases/download/"
        )
    ):
        raise RuntimeError("The installer download URL is not an approved GitHub Release URL.")

    try:
        size = int(asset.get("size", 0))
    except (TypeError, ValueError):
        size = 0

    if size < 1_000_000 or size > INSTALLER_UPDATE_MAX_BYTES:
        raise RuntimeError("The installer asset size is missing or outside the allowed range.")

    expected_hash = None
    digest = asset.get("digest")
    if isinstance(digest, str):
        algorithm, separator, value = digest.partition(":")
        if separator and algorithm.lower() == "sha256":
            expected_hash = value.strip().lower()

    # Older Release API responses may not include the digest field.
    if not expected_hash:
        checksum_name = INSTALLER_EXE_NAME + ".sha256"
        checksum_assets = [
            item for item in assets
            if item.get("name") == checksum_name
        ]
        if len(checksum_assets) == 1:
            checksum_url = checksum_assets[0].get("browser_download_url")
            if checksum_url and checksum_url.startswith(
                "https://github.com/pshoonb/Minecraft-Game/releases/download/"
            ):
                with github_request(checksum_url, timeout=30) as response:
                    checksum_text = response.read(4096).decode(
                        "utf-8", errors="replace"
                    )
                match = re.search(r"\b([0-9a-fA-F]{64})\b", checksum_text)
                if match:
                    expected_hash = match.group(1).lower()

    if not expected_hash or not re.fullmatch(r"[0-9a-f]{64}", expected_hash):
        raise RuntimeError("The latest installer Release does not provide a valid SHA-256 checksum.")

    return {
        "tag": str(tag),
        "url": download_url,
        "sha256": expected_hash,
        "size": size
    }


def download_and_stage_installer_update(release_info, current_exe):
    """Download and verify the new EXE, then stage it beside the running EXE."""

    update_dir = INSTALLER_CACHE_DIR / "InstallerUpdates"
    update_dir.mkdir(parents=True, exist_ok=True)

    update_token = datetime.now().strftime("%Y%m%d%H%M%S") + f"-{os.getpid()}"
    download_path = update_dir / f"{INSTALLER_EXE_NAME}.{update_token}.download"
    staged_path = current_exe.with_name(
        current_exe.name + f".update-staged-{update_token}"
    )
    helper_path = update_dir / f"apply-update-{update_token}.ps1"

    try:
        request = urllib.request.Request(
            release_info["url"],
            headers={"User-Agent": "RPG-Core-Installer"}
        )

        downloaded_size = 0
        with urllib.request.urlopen(request, timeout=120) as response:
            content_length = response.headers.get("Content-Length")
            if content_length:
                try:
                    if int(content_length) != release_info["size"]:
                        raise RuntimeError("The downloaded installer size does not match the Release metadata.")
                except ValueError:
                    pass

            with open(download_path, "wb") as output:
                while True:
                    block = response.read(1024 * 1024)
                    if not block:
                        break
                    downloaded_size += len(block)
                    if downloaded_size > INSTALLER_UPDATE_MAX_BYTES:
                        raise RuntimeError("The downloaded installer exceeds the maximum allowed size.")
                    output.write(block)

        if downloaded_size != release_info["size"]:
            raise RuntimeError("The downloaded installer size does not match the Release asset.")

        actual_hash = file_hash(download_path).lower()
        if actual_hash != release_info["sha256"]:
            raise RuntimeError("The downloaded installer failed SHA-256 verification.")

        with open(download_path, "rb") as downloaded:
            if downloaded.read(2) != b"MZ":
                raise RuntimeError("The downloaded file is not a valid Windows executable.")

        # Stage the verified file on the same volume as the running EXE. This
        # catches folder-permission problems before asking the updater to exit.
        shutil.copyfile(download_path, staged_path)
        if file_hash(staged_path).lower() != release_info["sha256"]:
            raise RuntimeError("The staged installer failed SHA-256 verification.")

        helper_source = r"""param(
    [int]$ParentPid,
    [string]$CurrentExe,
    [string]$StagedExe,
    [string]$UpdateToken,
    [string]$LogPath
)

$ErrorActionPreference = 'Stop'
$backupPath = "$CurrentExe.update-previous-$UpdateToken"
$deadline = (Get-Date).AddSeconds(60)

try {
    while (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue) {
        if ((Get-Date) -gt $deadline) {
            throw 'The old installer did not exit in time.'
        }
        Start-Sleep -Milliseconds 250
    }

    if (-not (Test-Path -LiteralPath $StagedExe -PathType Leaf)) {
        throw 'The verified staged installer is missing.'
    }
    if (Test-Path -LiteralPath $backupPath) {
        throw 'A temporary rollback file already exists; refusing to overwrite it.'
    }

    [System.IO.File]::Move($CurrentExe, $backupPath)

    try {
        [System.IO.File]::Move($StagedExe, $CurrentExe)
        $workingDirectory = Split-Path -LiteralPath $CurrentExe -Parent
        $newProcess = Start-Process -FilePath $CurrentExe -WorkingDirectory $workingDirectory -PassThru
        Start-Sleep -Milliseconds 1500

        if ($newProcess.HasExited) {
            throw 'The updated installer exited immediately after launch.'
        }

        Remove-Item -LiteralPath $backupPath -Force
        ('Installer update succeeded: ' + $CurrentExe) | Set-Content -LiteralPath $LogPath -Encoding UTF8
    }
    catch {
        if (Test-Path -LiteralPath $CurrentExe) {
            Remove-Item -LiteralPath $CurrentExe -Force
        }
        if (Test-Path -LiteralPath $backupPath) {
            [System.IO.File]::Move($backupPath, $CurrentExe)
        }
        throw
    }
}
catch {
    ($_ | Out-String) | Set-Content -LiteralPath $LogPath -Encoding UTF8
    exit 1
}
""";

        helper_path.write_text(helper_source, encoding="utf-8-sig")

        log_path = update_dir / "installer-update.log"
        powershell = os.path.join(
            os.environ.get("WINDIR", r"C:\Windows"),
            "System32",
            "WindowsPowerShell",
            "v1.0",
            "powershell.exe"
        )

        if not Path(powershell).is_file():
            powershell = "powershell.exe"

        creation_flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        subprocess.Popen(
            [
                powershell,
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle", "Hidden",
                "-ExecutionPolicy", "Bypass",
                "-File", str(helper_path),
                "-ParentPid", str(os.getpid()),
                "-CurrentExe", str(current_exe),
                "-StagedExe", str(staged_path),
                "-UpdateToken", update_token,
                "-LogPath", str(log_path)
            ],
            cwd=str(BASE_DIR),
            close_fds=True,
            creationflags=creation_flags
        )

        return True

    except Exception:
        staged_path.unlink(missing_ok=True)
        helper_path.unlink(missing_ok=True)
        raise

    finally:
        download_path.unlink(missing_ok=True)


def check_frozen_installer_update():
    """Check for a newer stable EXE and hand off replacement to PowerShell."""

    try:
        release_info = get_latest_installer_release()
        if not release_info:
            return False

        current_exe = Path(sys.executable).resolve()
        if not current_exe.is_file():
            raise RuntimeError("The currently running installer EXE could not be located.")

        current_hash = file_hash(current_exe).lower()
        if current_hash == release_info["sha256"]:
            print("The installer is already up to date.", flush=True)
            return False

        local_version = get_embedded_installer_version()
        latest_version = release_info["tag"]

        should_update = ask_yes_no(
            "A new RPG Core installer is available on GitHub.\n\n"
            f"Current installer: {local_version}\n"
            f"Latest version: {latest_version}\n\n"
            "The new EXE will be downloaded and checked with SHA-256 before replacing this file.\n"
            "The installer will close briefly and then restart with the updated version.\n\n"
            "Select “Yes” to update now, or “No” to continue with this version."
        )

        if not should_update:
            print("The user chose to continue with the current installer.", flush=True)
            return False

        if download_and_stage_installer_update(release_info, current_exe):
            show_info(
                "The new installer has been downloaded and verified.\n\n"
                "This window will close while the updater replaces the EXE. "
                "The updated installer will then reopen automatically."
            )
            return True

        return False

    except Exception as exc:
        show_warning(
            "The installer update check could not be completed.\n\n"
            f"Reason: {exc}\n\n"
            "The current installer will be kept, and the installation will continue."
        )
        print("Packaged installer update check failed; continuing with the current version: ", exc, flush=True)
        return False


def check_installer_update():
    """
    Compare the local installer with the version published on GitHub.
    Let the user sync the update or continue with the current version.
    Back up the previous file and restart the updated installer after a successful update.
    """
    # The packaged EXE is updated by publishing a new Windows release asset.
    # Never replace an executable with the raw Python source file.
    if IS_FROZEN:
        return check_frozen_installer_update()

    installer_path = Path(__file__).resolve()
    temporary_path = installer_path.with_name(
        installer_path.name + ".update-download"
    )

    try:
        request = urllib.request.Request(
            INSTALLER_UPDATE_URL,
            headers={"User-Agent": "RPG-Core-Installer"}
        )

        with urllib.request.urlopen(request, timeout=20) as response:
            remote_bytes = response.read(5 * 1024 * 1024 + 1)

        if not remote_bytes or len(remote_bytes) > 5 * 1024 * 1024:
            raise RuntimeError("GitHub returned an empty installer file or a file larger than 5 MB.")

        local_bytes = installer_path.read_bytes()

        if remote_bytes == local_bytes:
            print("The installer is already up to date.", flush=True)
            return False

        local_hash = hashlib.sha256(local_bytes).hexdigest()
        remote_hash = hashlib.sha256(remote_bytes).hexdigest()

        should_update = ask_yes_no(
            "A different installer version was found on GitHub.\n\n"
            f"Local SHA-256: {local_hash[:16]}...\n"
            f"GitHub SHA-256: {remote_hash[:16]}...\n\n"
            "Syncing will replace the current Python installer file; "
            "the current file will be backed up first.\n\n"
            "Select “Yes”: download the update, replace the file, and restart the installer.\n"
            "Select “No”: keep the current file and continue this installation."
        )

        if not should_update:
            print("The user chose to continue with the current installer.", flush=True)
            return False

        # 在覆盖本地文件前，先检查远端源码能否通过 Python 语法编译。
        compile(remote_bytes, str(installer_path), "exec")

        temporary_path.write_bytes(remote_bytes)
        if file_hash(temporary_path) != remote_hash:
            raise RuntimeError("The downloaded installer failed its integrity check.")

        os.replace(temporary_path, installer_path)

        # Replace the old installer directly; do not create versioned backups.
        for old_backup in installer_path.parent.glob(installer_path.name + ".backup-*"):
            try:
                old_backup.unlink()
                print("Removed an old installer backup: ", old_backup.name, flush=True)
            except OSError as exc:
                print("Could not remove an old installer backup: ", exc, flush=True)

        show_info(
            "The installer has been updated successfully!\\n\\n"
            "The previous installer file was replaced without creating a backup.\\n\\n"
            "Click “OK” to restart the updated installer."
        )

        try:
            subprocess.Popen(
                [sys.executable, str(installer_path)],
                cwd=str(BASE_DIR),
                close_fds=True
            )
        except Exception as exc:
            show_error(
                "The installer was updated, but it could not restart automatically.\n\n"
                f"Reason: {exc}\n\n"
                "Close this window, then double-click the updated Python installer to run it."
            )

        return True

    except Exception as exc:
        temporary_path.unlink(missing_ok=True)
        show_warning(
            "An error occurred while checking for or syncing the installer update.\n\n"
            f"Reason: {exc}\n\n"
            "The current installer will be kept, and the installation will continue."
        )
        print("Installer update check failed. Continuing with the local version: ", exc, flush=True)
        return False


# =====================================================
# FILE HASH / JAR VALIDATION


# =====================================================
# FILE HASH / JAR VALIDATION
# =====================================================

def file_hash(path):
    digest = hashlib.sha256()

    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(block)

    return digest.hexdigest()


def valid_jar(path):
    try:
        with zipfile.ZipFile(path, "r") as jar:
            return jar.testzip() is None
    except Exception:
        return False


# =====================================================
# FIND NEOFORGE INSTALLER AND OPTIONAL LOCAL MOD
# =====================================================

def find_files():
    installer_candidates = [BASE_DIR / INSTALLER_NAME]
    if IS_FROZEN:
        # In a one-file PyInstaller build, bundled data is extracted to _MEIPASS.
        bundle_dir = Path(getattr(sys, "_MEIPASS", BASE_DIR))
        installer_candidates.insert(0, bundle_dir / INSTALLER_NAME)

    installer = next(
        (candidate for candidate in installer_candidates if candidate.is_file()),
        BASE_DIR / INSTALLER_NAME
    )

    if not installer.is_file():
        raise FileNotFoundError(
            f"NeoForge installer not found:\n{INSTALLER_NAME}\n\n"
            "Place it in the same folder as this Python script."
        )

    if not valid_jar(installer):
        raise RuntimeError(
            "The NeoForge installer is damaged or is not a valid JAR file."
        )

    # 本地模组变为可选项：没有本地 JAR 时仍可从 GitHub Release 安装。
    candidates = [
        path
        for path in BASE_DIR.glob("*.jar")
        if path.resolve() != installer.resolve()
        and path.name.lower().startswith(MOD_JAR_PREFIX)
    ]

    if len(candidates) > 1:
        names = "\n".join(item.name for item in candidates)
        raise RuntimeError(
            "Multiple RPG Core JAR files were found in the script folder, so the local fallback cannot be determined:\n\n"
            + names
            + "\n\nKeep only one local fallback JAR, or move the older files out of this folder."
        )

    local_mod = candidates[0] if candidates else None

    if local_mod and not valid_jar(local_mod):
        raise RuntimeError(
            f"The local RPG Core file is damaged or is not a valid JAR:\n{local_mod.name}"
        )

    return installer, local_mod


# =====================================================
# GITHUB RELEASE UPDATE
# =====================================================

def github_request(url, timeout=30):
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "RPG-Core-Installer",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28"
        }
    )
    return urllib.request.urlopen(request, timeout=timeout)


def get_latest_release():
    """Fetch the latest public GitHub Release without requiring a user token."""

    with github_request(GITHUB_RELEASE_API, timeout=30) as response:
        release = json.loads(response.read().decode("utf-8"))

    tag = release.get("tag_name")
    assets = release.get("assets", [])

    if not tag or not isinstance(assets, list):
        raise RuntimeError("The GitHub Release data is incomplete or invalid.")

    jar_assets = [
        asset for asset in assets
        if str(asset.get("name", "")).lower().startswith(MOD_JAR_PREFIX)
        and str(asset.get("name", "")).lower().endswith(".jar")
    ]

    if len(jar_assets) != 1:
        raise RuntimeError(
            "The latest Release does not contain exactly one RPG Core JAR file."
        )

    jar_asset = jar_assets[0]
    jar_name = jar_asset.get("name")
    jar_url = jar_asset.get("browser_download_url")

    if (
        not jar_name
        or Path(jar_name).name != jar_name
        or not jar_url
    ):
        raise RuntimeError("The mod filename or download URL in the latest Release is invalid.")

    expected_hash = None

    # GitHub Release API 为资产提供 SHA-256 digest。
    asset_digest = jar_asset.get("digest")
    if isinstance(asset_digest, str) and asset_digest.lower().startswith("sha256:"):
        expected_hash = asset_digest.split(":", 1)[1].strip().lower()

    # 兼容 digest 字段不可用的情况，改读随 Release 发布的 .sha256 文件。
    if not expected_hash:
        checksum_name = jar_name + ".sha256"
        checksum_assets = [
            asset for asset in assets
            if asset.get("name") == checksum_name
        ]

        if checksum_assets:
            checksum_url = checksum_assets[0].get("browser_download_url")
            if checksum_url:
                with github_request(checksum_url, timeout=30) as response:
                    checksum_text = response.read(4096).decode(
                        "utf-8", errors="replace"
                    )
                match = re.search(
                    r"\b([0-9a-fA-F]{64})\b",
                    checksum_text
                )
                if match:
                    expected_hash = match.group(1).lower()

    if (
        not expected_hash
        or not re.fullmatch(r"[0-9a-f]{64}", expected_hash)
    ):
        raise RuntimeError(
            "The latest Release does not provide a valid SHA-256 checksum. The download was stopped."
        )

    return {
        "tag": tag,
        "name": jar_name,
        "url": jar_url,
        "sha256": expected_hash,
        "html_url": release.get("html_url", "")
    }


def download_release_mod(release_info):
    """Download to a temporary file and cache it only after verification."""

    MOD_CACHE_DIR.mkdir(parents=True, exist_ok=True)

    final_path = MOD_CACHE_DIR / release_info["name"]
    temporary_path = MOD_CACHE_DIR / (release_info["name"] + ".download")

    try:
        # 已有且校验正确的缓存文件可以复用。
        if (
            final_path.is_file()
            and file_hash(final_path).lower() == release_info["sha256"]
            and valid_jar(final_path)
        ):
            return final_path

        temporary_path.unlink(missing_ok=True)

        print("Downloading the latest RPG Core mod from GitHub...", flush=True)

        with github_request(release_info["url"], timeout=120) as response:
            with open(temporary_path, "wb") as output:
                shutil.copyfileobj(response, output)

        actual_hash = file_hash(temporary_path)
        expected_hash = release_info["sha256"]

        if actual_hash.lower() != expected_hash.lower():
            raise RuntimeError(
                "The mod failed its SHA-256 check. The downloaded file will not be installed."
            )

        if not valid_jar(temporary_path):
            raise RuntimeError(
                "The downloaded file is not a valid JAR. Installation was cancelled."
            )

        # 先完整下载并校验，再原子替换缓存文件。
        os.replace(temporary_path, final_path)
        return final_path

    finally:
        temporary_path.unlink(missing_ok=True)


def get_managed_installed_mod(state):
    """Return a valid managed mod recorded in the state file, if one exists."""

    record = state.get("managed_mod", {})
    if not isinstance(record, dict):
        return None

    filename = record.get("filename")
    if not filename or Path(filename).name != filename:
        return None

    candidate = GAME_DIR / "mods" / filename

    if (
        candidate.is_file()
        and candidate.name.lower().startswith(MOD_JAR_PREFIX)
        and valid_jar(candidate)
    ):
        return candidate

    return None


def choose_mod_to_install(local_mod, state):
    """
    Check the latest public GitHub Release.
    Reuse the latest version if an identical copy already exists locally or in the game folder.
    Keep using an available previous version if the download fails.
    """

    installed_mod = get_managed_installed_mod(state)
    fallback_mod = local_mod or installed_mod

    try:
        release = get_latest_release()
    except Exception as exc:
        warning = (
            "Unable to check the latest version on GitHub.\n\n"
            f"Reason: {exc}\n\n"
        )

        if fallback_mod:
            show_warning(
                warning + "The locally available or currently installed mod version will be used."
            )
            return fallback_mod

        raise RuntimeError(
            warning + "No local mod file is available. Check your internet connection and try again."
        )

    latest_hash = release["sha256"]

    if installed_mod and file_hash(installed_mod).lower() == latest_hash:
        print(
            f"RPG Core in the game folder is already up to date: {release['tag']}",
            flush=True
        )
        return installed_mod

    if local_mod and file_hash(local_mod).lower() == latest_hash:
        print(
            f"The local RPG Core file is already up to date: {release['tag']}",
            flush=True
        )
        return local_mod

    if installed_mod:
        old_description = f"Current game folder: {installed_mod.name}"
    elif local_mod:
        old_description = f"Local fallback file: {local_mod.name}"
    else:
        old_description = "No local previous version was found"

    prompt = (
        "A new version of RPG Core is available!\n\n"
        f"Latest version: {release['tag']}\n"
        f"Download file: {release['name']}\n\n"
        f"{old_description}\n\n"
        "Download and install the latest version?\n"
        "The downloaded file will be verified with SHA-256 before installation."
    )

    should_update = ask_yes_no(prompt)

    if should_update:
        try:
            return download_release_mod(release)
        except Exception as exc:
            show_warning(
                "The new version could not be downloaded or verified. Your existing mod file will not be deleted.\n\n"
                f"Reason: {exc}\n\n"
                + (
                    "The current local or installed version will be used."
                    if fallback_mod
                    else "No previous mod version is available. Installation has been stopped."
                )
            )

            if fallback_mod:
                return fallback_mod

            raise RuntimeError(
                "The latest version could not be downloaded, and no local mod version is available."
            )

    if fallback_mod:
        print("The user chose not to update. Continuing with the existing mod.", flush=True)
        return fallback_mod

    raise RuntimeError(
        "You chose not to download the latest version, but no local or installed mod is available."
    )


# =====================================================
# JAVA VERSION
# =====================================================

def get_java_version(java):
    try:
        result = subprocess.run(
            [str(java), "-version"],
            capture_output=True,
            text=True,
            timeout=15
        )

        output = result.stdout + "\n" + result.stderr

        match = re.search(
            r'version\s+"(?:1\.)?(\d+)',
            output
        )

        if match:
            return int(match.group(1))

        match = re.search(
            r'openjdk\s+(\d+)',
            output,
            re.IGNORECASE
        )

        if match:
            return int(match.group(1))

    except Exception:
        pass

    return None


# =====================================================
# FIND EXISTING JAVA 21
# =====================================================

def find_java():
    candidates = []

    system_java = shutil.which("java")
    if system_java:
        candidates.append(Path(system_java))

    for variable in ("JAVA_HOME", "JDK_HOME"):
        value = os.environ.get(variable)
        if value:
            candidates.append(Path(value) / "bin" / "java.exe")

    runtime_dir = GAME_DIR / "runtime"
    if runtime_dir.exists():
        candidates.extend(runtime_dir.rglob("java.exe"))

    program_files = Path(
        os.environ.get("ProgramFiles", r"C:\Program Files")
    )

    for folder in (
        program_files / "Eclipse Adoptium",
        program_files / "Java"
    ):
        if folder.exists():
            candidates.extend(folder.glob("*/bin/java.exe"))

    checked = set()

    for java in candidates:
        try:
            key = str(java.resolve()).lower()
            if key in checked:
                continue
            checked.add(key)

            if not java.is_file():
                continue

            if get_java_version(java) == 21:
                return java

        except Exception:
            continue

    return None


# =====================================================
# SAFE ZIP EXTRACTION
# =====================================================

def safe_extract(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    root_path = destination.resolve()

    with zipfile.ZipFile(archive, "r") as z:
        for item in z.infolist():
            target = (destination / item.filename).resolve()

            if target != root_path and root_path not in target.parents:
                raise RuntimeError(
                    "The Java archive contains an unsafe file path."
                )

        z.extractall(destination)


# =====================================================
# FIND JAVA IN CACHE
# =====================================================

def find_cached_java():
    if not JAVA_DIR.exists():
        return None

    for java in JAVA_DIR.rglob("java.exe"):
        if get_java_version(java) == 21:
            return java

    return None


# =====================================================
# AUTOMATIC JAVA 21 DOWNLOAD
# =====================================================

def install_java():
    JAVA_DIR.mkdir(parents=True, exist_ok=True)

    cached = find_cached_java()
    if cached:
        return cached

    print("Getting Java 21 download information...", flush=True)

    request = urllib.request.Request(
        JAVA_API,
        headers={"User-Agent": "RPG-Core-Installer"}
    )

    with urllib.request.urlopen(request, timeout=40) as response:
        assets = json.loads(response.read().decode("utf-8"))

    if not assets:
        raise RuntimeError("Unable to retrieve Java 21 download information.")

    package = assets[0]["binary"]["package"]
    download_url = package["link"]
    expected_hash = package.get("checksum")
    archive = JAVA_DIR / "java21.zip"
    temporary_archive = JAVA_DIR / "java21.zip.download"

    try:
        print("Downloading Java 21...", flush=True)

        request = urllib.request.Request(
            download_url,
            headers={"User-Agent": "RPG-Core-Installer"}
        )

        with urllib.request.urlopen(request, timeout=120) as response:
            with open(temporary_archive, "wb") as f:
                shutil.copyfileobj(response, f)

        if expected_hash:
            actual_hash = file_hash(temporary_archive)

            if actual_hash.lower() != expected_hash.lower():
                raise RuntimeError(
                    "The Java download failed its integrity check. The downloaded file has been removed."
                )

        os.replace(temporary_archive, archive)

        print("Extracting Java 21...", flush=True)
        safe_extract(archive, JAVA_DIR)

    finally:
        temporary_archive.unlink(missing_ok=True)
        archive.unlink(missing_ok=True)

    java = find_cached_java()
    if not java:
        raise RuntimeError(
            "Java 21 was installed, but java.exe could not be found."
        )

    return java


# =====================================================
# STATE
# =====================================================

def get_state_path():
    return GAME_DIR / STATE_NAME


def load_state():
    state_path = get_state_path()

    if not state_path.exists():
        return {}

    try:
        data = json.loads(state_path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}

    except Exception:
        backup = state_path.with_name(
            STATE_NAME
            + ".corrupt-"
            + datetime.now().strftime("%Y%m%d-%H%M%S")
        )
        state_path.replace(backup)
        return {}


def save_state(state):
    GAME_DIR.mkdir(parents=True, exist_ok=True)
    state_path = get_state_path()
    temporary = state_path.with_suffix(".tmp")

    temporary.write_text(
        json.dumps(state, ensure_ascii=False, indent=4),
        encoding="utf-8"
    )

    temporary.replace(state_path)


# =====================================================
# CHECK NEOFORGE
# =====================================================

def neoforge_installed():
    versions_dir = GAME_DIR / "versions"

    if not versions_dir.exists():
        return False

    for folder in versions_dir.glob(f"*{NEOFORGE_VERSION}*"):
        if not folder.is_dir():
            continue

        for json_file in folder.glob("*.json"):
            try:
                data = json.loads(
                    json_file.read_text(encoding="utf-8")
                )

                version_id = data.get("id", "")

                if (
                    NEOFORGE_VERSION in version_id
                    and data.get("inheritsFrom") == "1.21.1"
                ):
                    return True

            except Exception:
                continue

    return False


# =====================================================
# INSTALL NEOFORGE
# =====================================================

def install_neoforge(java, installer, state):
    previous = state.get("neoforge", {})
    installer_hash = file_hash(installer)

    if (
        neoforge_installed()
        and isinstance(previous, dict)
        and previous.get("version") == NEOFORGE_VERSION
    ):
        print("NeoForge is already installed. Skipping reinstallation.", flush=True)
        return

    if neoforge_installed():
        print("An existing NeoForge installation was detected.", flush=True)

        state["neoforge"] = {
            "version": NEOFORGE_VERSION,
            "installer_hash": installer_hash
        }
        save_state(state)
        return

    print("Installing the NeoForge client...", flush=True)

    command = [
        str(java),
        "-jar",
        str(installer),
        "--install-client",
        str(GAME_DIR)
    ]

    result = subprocess.run(
        command,
        cwd=str(BASE_DIR),
        check=False
    )

    if result.returncode != 0:
        raise RuntimeError(
            "NeoForge installation failed.\n"
            f"Exit code: {result.returncode}"
        )

    if not neoforge_installed():
        raise RuntimeError(
            "The installer finished, but the expected NeoForge version profile was not found."
        )

    state["neoforge"] = {
        "version": NEOFORGE_VERSION,
        "installer_hash": installer_hash,
        "installed_at": datetime.now().isoformat()
    }
    save_state(state)

    print("NeoForge installed successfully.", flush=True)


# =====================================================
# INSTALL RPG CORE MOD
# =====================================================

def install_mod(mod_file, state):
    mods_dir = GAME_DIR / "mods"
    mods_dir.mkdir(parents=True, exist_ok=True)

    destination = mods_dir / mod_file.name
    source_hash = file_hash(mod_file)

    old_record = state.get("managed_mod", {})
    if not isinstance(old_record, dict):
        old_record = {}

    # 如果上次安装的文件换了名字，只清理确定由程序管理且
    # 没有被玩家修改的旧文件。
    old_name = old_record.get("filename")
    old_hash = old_record.get("hash")

    if (
        old_name
        and old_name != mod_file.name
        and Path(old_name).name == old_name
    ):
        old_file = mods_dir / old_name

        if old_file.is_file() and old_hash:
            if file_hash(old_file) == old_hash:
                old_file.unlink()
                print("Removed the previous mod file managed by this installer.", flush=True)

    if destination.exists() and file_hash(destination) == source_hash:
        print(
            "RPG Core is already present and identical. Skipping file copy.",
            flush=True
        )
    else:
        # Copy and verify first, then atomically replace the old JAR in place.
        # No versioned backup is created.
        temporary = destination.with_name(destination.name + ".installing")

        try:
            temporary.unlink(missing_ok=True)
            shutil.copy2(mod_file, temporary)

            if file_hash(temporary) != source_hash or not valid_jar(temporary):
                raise RuntimeError("The mod failed verification after being copied to the game folder.")

            os.replace(temporary, destination)
            print("RPG Core installed successfully; the previous JAR was replaced.", flush=True)

        finally:
            temporary.unlink(missing_ok=True)

    # Remove only backup files that match the naming pattern previously created
    # by this installer (for example: rpgcore-0.2.7.jar.backup-20261110-031500).
    legacy_backup_pattern = re.compile(
        r"^rpgcore-.+\.jar\.backup-\d{8}-\d{6}(?:-\d+)?$",
        re.IGNORECASE
    )
    for old_backup in mods_dir.iterdir():
        if old_backup.is_file() and legacy_backup_pattern.fullmatch(old_backup.name):
            try:
                old_backup.unlink()
                print("Removed an old RPG Core backup: ", old_backup.name, flush=True)
            except OSError as exc:
                print("Could not remove an old RPG Core backup: ", exc, flush=True)

    state["managed_mod"] = {
        "filename": mod_file.name,
        "hash": source_hash,
        "installed_at": datetime.now().isoformat()
    }
    save_state(state)


# =====================================================
# MAIN
# =====================================================

def main():
    global GAME_DIR

    if os.name != "nt":
        raise RuntimeError("This installer currently supports Windows only.")

    # 启动时提供可选的安装器同步更新；用户拒绝或网络失败均继续旧版。
    if check_installer_update():
        return

    print("=" * 55)
    print("RPG CORE ONE CLICK INSTALLER")
    print("Minecraft 1.21.1")
    print("NeoForge", NEOFORGE_VERSION)
    print("=" * 55)

    # 1. 查找 NeoForge 安装器与可选的本地模组备用文件。
    installer, local_mod = find_files()
    print("NeoForge installer found: ", installer.name, flush=True)

    if local_mod:
        print("Local fallback mod found: ", local_mod.name, flush=True)
    else:
        print("No local mod found. The latest version will be retrieved from GitHub Releases.", flush=True)

    # 2. 先选择游戏目录，再进行所有依赖该目录的操作。
    selected_dir = choose_game_directory(DEFAULT_GAME_DIR)

    if selected_dir is None:
        print("The user cancelled the installation.", flush=True)
        return

    GAME_DIR = selected_dir
    print("Game directory: ", GAME_DIR, flush=True)

    # 3. 确认 TLauncher 已关闭。
    response = ask_ok_cancel(
        "RPG Core is ready to be installed.\n\n"
        "Please close TLauncher and Minecraft before continuing.\n\n"
        "Installation directory:\n"
        + str(GAME_DIR)
        + "\n\nContinue?"
    )

    if not response:
        print("The user cancelled the installation.", flush=True)
        return

    GAME_DIR.mkdir(parents=True, exist_ok=True)

    # 4. 读取所选游戏目录中的安装状态。
    state = load_state()

    # 5. 查询 GitHub 最新 Release，下载前询问用户并校验 SHA-256。
    mod_file = choose_mod_to_install(local_mod, state)
    print("Mod selected for this installation: ", mod_file.name, flush=True)

    # 6. 准备 Java。
    print("Checking for Java 21...", flush=True)
    java = find_java()

    if java:
        print("Java 21 found: ", java, flush=True)
    else:
        print("Java 21 was not found. Downloading it automatically...", flush=True)
        java = install_java()

    # 7. 安装 NeoForge。
    install_neoforge(java, installer, state)

    # 8. 安装 RPG Core。
    install_mod(mod_file, state)

    # 9. 完成。
    show_info(
        "RPG Core installed successfully!\n\n"
        "Minecraft: 1.21.1\n"
        "NeoForge: " + NEOFORGE_VERSION + "\n"
        "Mod: " + mod_file.name + "\n\n"
        "Installation directory:\n" + str(GAME_DIR) + "\n\n"
        "Reopen TLauncher and launch the NeoForge 21.1.265 profile."
    )

    print("All installation steps completed.", flush=True)


# =====================================================
# START
# =====================================================

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("Installation interrupted by the user.", flush=True)
        show_error("Installation was interrupted. Check the game directory and run the installer again.")
    except Exception as exc:
        print("Installation failed: ", exc, flush=True)
        show_error("Installation failed: \n\n" + str(exc))
