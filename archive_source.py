#!/usr/bin/env python3
"""Положите рядом с settings.gradle и запустите. Нужен только Python 3."""
from datetime import datetime
from fnmatch import fnmatch
from pathlib import Path
import os
import zipfile

SKIP_DIRS = {'.gradle', 'build', '.idea', '.git', '.kotlin', '.cxx',
             '.externalNativeBuild', '.local-build', '__pycache__'}
SKIP_FILES = {'local.properties', '.DS_Store', 'key.properties',
              'keystore.properties', '.env'}
SKIP_PATTERNS = ('*.apk', '*.iml', '*.jks', '*.keystore', '*.pem',
                 '*.p12', '*.pfx', '*.zip', '.env.*')


def excluded(name):
    return name in SKIP_FILES or any(fnmatch(name.lower(), p) for p in SKIP_PATTERNS)


def archive(project):
    project = Path(project).resolve()
    if not any((project / name).is_file() for name in ('settings.gradle', 'settings.gradle.kts')):
        raise ValueError('Положите archive_source.py в корень Android-проекта, рядом с settings.gradle.')
    stamp = datetime.now().strftime('%Y-%m-%d_%H-%M-%S_%f')
    target = project.parent / (project.name + '_source_' + stamp + '.zip')
    count = 0
    try:
        with zipfile.ZipFile(target, 'x', zipfile.ZIP_DEFLATED) as result:
            for folder, dirs, files in os.walk(project, followlinks=False):
                dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS
                                 and not (Path(folder) / d).is_symlink())
                for name in sorted(files):
                    path = Path(folder) / name
                    if excluded(name) or path.is_symlink():
                        continue
                    # ZIP содержит файлы проекта сразу в корне, без лишней папки.
                    result.write(path, path.relative_to(project).as_posix())
                    count += 1
    except Exception:
        target.unlink(missing_ok=True)
        raise
    return target, count


def show(message, error=False):
    try:
        import tkinter as tk
        from tkinter import messagebox
        window = tk.Tk()
        window.withdraw()
        (messagebox.showerror if error else messagebox.showinfo)('Архив исходников', message)
        window.destroy()
    except Exception:
        print(message)
        try:
            input('\nНажмите Enter, чтобы закрыть окно…')
        except (EOFError, OSError):
            pass


if __name__ == '__main__':
    try:
        output, total = archive(Path(__file__).resolve().parent)
        show(f'Готово! Файлов: {total}\nАрхив:\n{output}')
    except Exception as exc:
        show(f'Не удалось создать архив:\n{exc}', error=True)
