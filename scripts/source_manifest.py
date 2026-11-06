"""Shared publication boundary for source checks and release-source packaging."""
from pathlib import Path

PUBLIC_ENTRIES = (
    'app/src', 'app/schemas', 'app/build.gradle.kts', 'app/proguard-rules.pro', 'app/gradle.lockfile',
    'gradle', 'scripts', 'docs', 'design', '.github', '.gitignore',
    'README.md', 'LICENSE', 'NOTICE', 'SECURITY.md', 'THIRD_PARTY_NOTICES.md', 'CONTRIBUTING.md',
    'DESIGNER-START-HERE.md', 'settings.gradle.kts', 'build.gradle.kts',
    'gradle.properties', 'gradlew', 'gradlew.bat',
)
PRIVATE_PARTS = {'.git', '.tooling', '.gradle', '.kotlin', '.claude', '.playwright-mcp',
                 '.idea', '.vscode', 'build', 'artifacts', '__pycache__'}
PRIVATE_SUFFIXES = {'.jks', '.keystore', '.pem', '.p12', '.pfx', '.db', '.sqlite', '.sqlite3',
                    '.zip', '.apk', '.aab', '.log', '.pyc'}
AUDIO_SUFFIXES = {'.wav', '.mp3', '.m4a', '.aac', '.flac', '.opus', '.ogg', '.amr', '.mp4'}
SYNTHETIC_AUDIO = {'app/src/androidTest/assets/speech/' + name for name in ('short-en.wav', 'short-he.wav', 'silence.wav')}


def allowed(path: Path) -> bool:
    name = path.as_posix()
    if any(part in PRIVATE_PARTS for part in path.parts):
        return False
    if path.name in {'.DS_Store', 'local.properties'} or path.name.startswith('.env'):
        return False
    if path.suffix.lower() in PRIVATE_SUFFIXES:
        return False
    if path.suffix.lower() in AUDIO_SUFFIXES and name not in SYNTHETIC_AUDIO:
        return False
    return any(name == prefix or name.startswith(prefix + '/') for prefix in PUBLIC_ENTRIES)


def public_files(root: Path):
    for entry in PUBLIC_ENTRIES:
        source = root / entry
        if not source.exists():
            raise ValueError('Missing source entry: ' + entry)
        for file in sorted(source.rglob('*') if source.is_dir() else [source]):
            relative = file.relative_to(root)
            if file.is_file() and allowed(relative) and not any((root / Path(*relative.parts[:i])).is_symlink() for i in range(1, len(relative.parts) + 1)):
                yield file
