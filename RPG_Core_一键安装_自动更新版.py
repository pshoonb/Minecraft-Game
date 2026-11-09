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
from tkinter import messagebox, filedialog

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
BASE_DIR = Path(__file__).resolve().parent

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
    """显示游戏目录选择窗口；取消时返回 None。"""

    window = tk.Tk()
    window.title("选择 Minecraft 游戏目录")
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
        text="请选择 Minecraft 游戏目录：",
        font=("Segoe UI", 10, "bold")
    ).pack(anchor="w", padx=16, pady=(15, 5))

    tk.Label(
        window,
        text="默认使用 .minecraft。若 TLauncher 使用独立游戏目录，请点击“浏览...”选择该目录。",
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
            title="选择 Minecraft 游戏目录",
            initialdir=str(initial_dir),
            mustexist=True
        )

        if selected:
            path_var.set(selected)

    def confirm():
        raw_path = path_var.get().strip().strip('"')

        if not raw_path:
            messagebox.showerror(
                APP_NAME,
                "游戏目录不能为空。",
                parent=window
            )
            return

        selected = Path(raw_path).expanduser()

        if selected.exists() and not selected.is_dir():
            messagebox.showerror(
                APP_NAME,
                "选择的路径不是文件夹，请重新选择。",
                parent=window
            )
            return

        if not selected.exists():
            create_it = messagebox.askyesno(
                APP_NAME,
                "这个目录目前不存在：\n\n"
                f"{selected}\n\n"
                "安装时需要创建这个目录。是否使用这个路径？",
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
        text="浏览...",
        command=browse,
        width=10
    ).pack(side="left", padx=(8, 0))

    button_frame = tk.Frame(window)
    button_frame.pack(pady=(0, 14))

    tk.Button(
        button_frame,
        text="使用此目录",
        command=confirm,
        width=16
    ).pack(side="left", padx=5)

    tk.Button(
        button_frame,
        text="取消",
        command=cancel,
        width=10
    ).pack(side="left", padx=5)

    window.protocol("WM_DELETE_WINDOW", cancel)
    window.mainloop()

    return result["path"]


def show_error(text):
    messagebox.showerror(APP_NAME, text)


def show_info(text):
    messagebox.showinfo(APP_NAME, text)


def check_installer_update():
    """
    检查 GitHub 上的安装器源码是否与本地不同。
    用户可以选择同步更新或继续使用当前版本。
    更新成功后备份旧文件并重新启动新版安装器。
    """
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
            raise RuntimeError("GitHub 返回的安装器文件为空或超过 5 MB。")

        local_bytes = installer_path.read_bytes()

        if remote_bytes == local_bytes:
            print("安装器已是 GitHub 当前版本。", flush=True)
            return False

        local_hash = hashlib.sha256(local_bytes).hexdigest()
        remote_hash = hashlib.sha256(remote_bytes).hexdigest()

        should_update = messagebox.askyesno(
            APP_NAME,
            "检测到 GitHub 上的安装器与本地文件不同。\\n\\n"
            f"本地 SHA-256：{local_hash[:16]}...\\n"
            f"GitHub SHA-256：{remote_hash[:16]}...\\n\\n"
            "同步更新会覆盖当前 Python 安装器文件；"
            "更新前会自动备份当前文件。\\n\\n"
            "选择“是”：下载并同步更新，随后自动重新启动。\\n"
            "选择“否”：保留当前文件，继续执行本次安装。"
        )

        if not should_update:
            print("用户选择继续使用当前安装器版本。", flush=True)
            return False

        # 在覆盖本地文件前，先检查远端源码能否通过 Python 语法编译。
        compile(remote_bytes, str(installer_path), "exec")

        temporary_path.write_bytes(remote_bytes)
        if file_hash(temporary_path) != remote_hash:
            raise RuntimeError("安装器下载文件校验失败。")

        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        backup_path = installer_path.with_name(
            installer_path.name + ".backup-" + timestamp
        )

        counter = 1
        while backup_path.exists():
            backup_path = installer_path.with_name(
                installer_path.name
                + ".backup-"
                + timestamp
                + "-"
                + str(counter)
            )
            counter += 1

        shutil.copy2(installer_path, backup_path)
        os.replace(temporary_path, installer_path)

        show_info(
            "安装器已同步更新！\\n\\n"
            "旧版备份：\\n"
            + str(backup_path)
            + "\\n\\n点击“确定”后将自动重新启动新版安装器。"
        )

        try:
            subprocess.Popen(
                [sys.executable, str(installer_path)],
                cwd=str(BASE_DIR),
                close_fds=True
            )
        except Exception as exc:
            show_error(
                "新版文件已更新，但无法自动重新启动。\\n\\n"
                f"原因：{exc}\\n\\n"
                "请关闭当前窗口，再双击新版 Python 安装器运行。"
            )

        return True

    except Exception as exc:
        temporary_path.unlink(missing_ok=True)
        messagebox.showwarning(
            APP_NAME,
            "检查或同步安装器更新时遇到问题。\\n\\n"
            f"原因：{exc}\\n\\n"
            "将保留当前安装器并继续原有安装流程。"
        )
        print("安装器更新检查失败，继续使用本地版本：", exc, flush=True)
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
    installer = BASE_DIR / INSTALLER_NAME

    if not installer.is_file():
        raise FileNotFoundError(
            f"找不到 NeoForge 安装器：\n{INSTALLER_NAME}\n\n"
            "请把它放在 Python 程序旁边。"
        )

    if not valid_jar(installer):
        raise RuntimeError(
            "NeoForge 安装器文件损坏或不是有效 JAR。"
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
            "程序目录中发现多个 RPG Core JAR，无法确定本地备用版本：\n\n"
            + names
            + "\n\n请只保留一个本地备用 JAR，或将旧版移出此目录。"
        )

    local_mod = candidates[0] if candidates else None

    if local_mod and not valid_jar(local_mod):
        raise RuntimeError(
            f"本地 RPG Core 文件损坏或不是有效 JAR：\n{local_mod.name}"
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
    """读取公开仓库最新正式 Release，不需要用户 Token。"""

    with github_request(GITHUB_RELEASE_API, timeout=30) as response:
        release = json.loads(response.read().decode("utf-8"))

    tag = release.get("tag_name")
    assets = release.get("assets", [])

    if not tag or not isinstance(assets, list):
        raise RuntimeError("GitHub Release 数据格式不完整。")

    jar_assets = [
        asset for asset in assets
        if str(asset.get("name", "")).lower().startswith(MOD_JAR_PREFIX)
        and str(asset.get("name", "")).lower().endswith(".jar")
    ]

    if len(jar_assets) != 1:
        raise RuntimeError(
            "最新 Release 中没有找到唯一的 RPG Core JAR 文件。"
        )

    jar_asset = jar_assets[0]
    jar_name = jar_asset.get("name")
    jar_url = jar_asset.get("browser_download_url")

    if (
        not jar_name
        or Path(jar_name).name != jar_name
        or not jar_url
    ):
        raise RuntimeError("最新 Release 的模组文件名或下载地址无效。")

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
            "最新 Release 缺少有效的 SHA-256 校验值，已停止下载。"
        )

    return {
        "tag": tag,
        "name": jar_name,
        "url": jar_url,
        "sha256": expected_hash,
        "html_url": release.get("html_url", "")
    }


def download_release_mod(release_info):
    """下载到临时文件，校验通过后才放入缓存。"""

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

        print("正在下载 GitHub 最新版模组...", flush=True)

        with github_request(release_info["url"], timeout=120) as response:
            with open(temporary_path, "wb") as output:
                shutil.copyfileobj(response, output)

        actual_hash = file_hash(temporary_path)
        expected_hash = release_info["sha256"]

        if actual_hash.lower() != expected_hash.lower():
            raise RuntimeError(
                "模组 SHA-256 校验失败，下载文件已拒绝安装。"
            )

        if not valid_jar(temporary_path):
            raise RuntimeError(
                "下载文件不是有效 JAR，已拒绝安装。"
            )

        # 先完整下载并校验，再原子替换缓存文件。
        os.replace(temporary_path, final_path)
        return final_path

    finally:
        temporary_path.unlink(missing_ok=True)


def get_managed_installed_mod(state):
    """仅返回状态文件记录过的、仍然有效的已管理模组。"""

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
    查询公开 GitHub Release。
    若本地或游戏目录中已有完全相同的最新版，则直接使用。
    下载失败时保留并使用可用的旧版本。
    """

    installed_mod = get_managed_installed_mod(state)
    fallback_mod = local_mod or installed_mod

    try:
        release = get_latest_release()
    except Exception as exc:
        warning = (
            "暂时无法检查 GitHub 最新版本。\n\n"
            f"原因：{exc}\n\n"
        )

        if fallback_mod:
            messagebox.showwarning(
                APP_NAME,
                warning + "将继续使用本地/已安装的模组版本。"
            )
            return fallback_mod

        raise RuntimeError(
            warning + "当前没有可用的本地模组文件，请检查网络后重试。"
        )

    latest_hash = release["sha256"]

    if installed_mod and file_hash(installed_mod).lower() == latest_hash:
        print(
            f"游戏目录中的 RPG Core 已是最新版：{release['tag']}",
            flush=True
        )
        return installed_mod

    if local_mod and file_hash(local_mod).lower() == latest_hash:
        print(
            f"本地 RPG Core 已是最新版：{release['tag']}",
            flush=True
        )
        return local_mod

    if installed_mod:
        old_description = f"当前游戏目录：{installed_mod.name}"
    elif local_mod:
        old_description = f"本地备用文件：{local_mod.name}"
    else:
        old_description = "当前没有检测到本地旧版本"

    prompt = (
        "发现 RPG Core 新版本！\n\n"
        f"最新版本：{release['tag']}\n"
        f"下载文件：{release['name']}\n\n"
        f"{old_description}\n\n"
        "是否下载并安装最新版？\n"
        "下载完成后会先校验 SHA-256，再安装模组。"
    )

    should_update = messagebox.askyesno(APP_NAME, prompt)

    if should_update:
        try:
            return download_release_mod(release)
        except Exception as exc:
            messagebox.showwarning(
                APP_NAME,
                "新版下载或校验失败，原有模组文件不会被删除。\n\n"
                f"原因：{exc}\n\n"
                + (
                    "将继续使用当前本地/已安装版本。"
                    if fallback_mod
                    else "目前没有可用的旧版模组，安装已停止。"
                )
            )

            if fallback_mod:
                return fallback_mod

            raise RuntimeError(
                "最新版下载失败，而且没有可用的本地模组版本。"
            )

    if fallback_mod:
        print("用户选择暂不更新，继续使用现有模组。", flush=True)
        return fallback_mod

    raise RuntimeError(
        "你选择了暂不下载最新版，但当前没有本地/已安装的模组可供安装。"
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
                    "Java 安装包包含不安全的文件路径。"
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

    print("正在获取 Java 21 下载信息...", flush=True)

    request = urllib.request.Request(
        JAVA_API,
        headers={"User-Agent": "RPG-Core-Installer"}
    )

    with urllib.request.urlopen(request, timeout=40) as response:
        assets = json.loads(response.read().decode("utf-8"))

    if not assets:
        raise RuntimeError("无法获取 Java 21 下载信息。")

    package = assets[0]["binary"]["package"]
    download_url = package["link"]
    expected_hash = package.get("checksum")
    archive = JAVA_DIR / "java21.zip"
    temporary_archive = JAVA_DIR / "java21.zip.download"

    try:
        print("正在下载 Java 21...", flush=True)

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
                    "Java 下载文件校验失败，已删除下载文件。"
                )

        os.replace(temporary_archive, archive)

        print("正在解压 Java 21...", flush=True)
        safe_extract(archive, JAVA_DIR)

    finally:
        temporary_archive.unlink(missing_ok=True)
        archive.unlink(missing_ok=True)

    java = find_cached_java()
    if not java:
        raise RuntimeError(
            "Java 21 安装完成，但无法找到 java.exe。"
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
        print("NeoForge 已安装，跳过重复安装。", flush=True)
        return

    if neoforge_installed():
        print("检测到 NeoForge 已存在。", flush=True)

        state["neoforge"] = {
            "version": NEOFORGE_VERSION,
            "installer_hash": installer_hash
        }
        save_state(state)
        return

    print("正在安装 NeoForge 客户端...", flush=True)

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
            "NeoForge 安装失败。\n"
            f"退出代码：{result.returncode}"
        )

    if not neoforge_installed():
        raise RuntimeError(
            "安装器结束，但未找到正确的 NeoForge 版本配置。"
        )

    state["neoforge"] = {
        "version": NEOFORGE_VERSION,
        "installer_hash": installer_hash,
        "installed_at": datetime.now().isoformat()
    }
    save_state(state)

    print("NeoForge 安装成功。", flush=True)


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
                print("已清理上一次管理的旧模组文件。", flush=True)

    if destination.exists() and file_hash(destination) == source_hash:
        print(
            "RPG Core 已存在且内容相同，跳过复制。",
            flush=True
        )
    else:
        # 先写入临时文件并校验，再替换目标，尽量避免复制中断留下半个 JAR。
        temporary = destination.with_name(destination.name + ".installing")

        try:
            temporary.unlink(missing_ok=True)
            shutil.copy2(mod_file, temporary)

            if file_hash(temporary) != source_hash or not valid_jar(temporary):
                raise RuntimeError("复制到游戏目录后的模组校验失败。")

            if destination.exists():
                timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
                backup = destination.with_name(
                    destination.name + ".backup-" + timestamp
                )

                counter = 1
                while backup.exists():
                    backup = destination.with_name(
                        destination.name
                        + ".backup-"
                        + timestamp
                        + "-"
                        + str(counter)
                    )
                    counter += 1

                destination.replace(backup)
                print("旧模组已备份。", flush=True)

            os.replace(temporary, destination)
            print("RPG Core 安装成功。", flush=True)

        finally:
            temporary.unlink(missing_ok=True)

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
        raise RuntimeError("此安装器目前只支持 Windows。")

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
    print("找到 NeoForge：", installer.name, flush=True)

    if local_mod:
        print("找到本地模组备用文件：", local_mod.name, flush=True)
    else:
        print("未找到本地模组，将从 GitHub Releases 获取最新版。", flush=True)

    # 2. 先选择游戏目录，再进行所有依赖该目录的操作。
    selected_dir = choose_game_directory(DEFAULT_GAME_DIR)

    if selected_dir is None:
        print("用户取消安装。", flush=True)
        return

    GAME_DIR = selected_dir
    print("游戏目录：", GAME_DIR, flush=True)

    # 3. 确认 TLauncher 已关闭。
    response = messagebox.askokcancel(
        APP_NAME,
        "即将自动安装 RPG Core。\n\n"
        "请先关闭 TLauncher 和正在运行的 Minecraft。\n\n"
        "安装目录：\n"
        + str(GAME_DIR)
        + "\n\n确定继续吗？"
    )

    if not response:
        print("用户取消安装。", flush=True)
        return

    GAME_DIR.mkdir(parents=True, exist_ok=True)

    # 4. 读取所选游戏目录中的安装状态。
    state = load_state()

    # 5. 查询 GitHub 最新 Release，下载前询问用户并校验 SHA-256。
    mod_file = choose_mod_to_install(local_mod, state)
    print("本次准备安装：", mod_file.name, flush=True)

    # 6. 准备 Java。
    print("正在检查 Java 21...", flush=True)
    java = find_java()

    if java:
        print("已找到 Java 21：", java, flush=True)
    else:
        print("没有找到 Java 21，自动下载...", flush=True)
        java = install_java()

    # 7. 安装 NeoForge。
    install_neoforge(java, installer, state)

    # 8. 安装 RPG Core。
    install_mod(mod_file, state)

    # 9. 完成。
    show_info(
        "RPG Core 安装成功！\n\n"
        "Minecraft：1.21.1\n"
        "NeoForge：" + NEOFORGE_VERSION + "\n"
        "模组：" + mod_file.name + "\n\n"
        "安装目录：\n" + str(GAME_DIR) + "\n\n"
        "请重新打开 TLauncher，选择 NeoForge 21.1.265 配置启动。"
    )

    print("所有安装步骤已完成。", flush=True)


# =====================================================
# START
# =====================================================

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("安装被用户中断。", flush=True)
        show_error("安装已被中断。请检查游戏目录后再重新运行。")
    except Exception as exc:
        print("安装失败：", exc, flush=True)
        show_error("安装失败：\n\n" + str(exc))
