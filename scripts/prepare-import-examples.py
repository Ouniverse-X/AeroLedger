"""Place import preview examples in an emulator's Downloads; leaves app data intact."""
import argparse
import csv
from datetime import date
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", default="emulator-5580")
    args = parser.parse_args()
    today = date.today().isoformat()
    normal = [
        ["demo-import-lunch", "expense", "18.50", today, "餐饮", "午餐"],
        ["demo-import-metro", "expense", "4.00", today, "交通", "地铁"],
        ["demo-import-work", "income", "200.00", today, "兼职", "助教劳务"],
    ]
    examples = {
        "01-normal.csv": normal,
        "02-mixed.csv": [
            ["demo-mixed-lunch", "expense", "18.50", today, "餐饮", "午餐"],
            ["demo-mixed-lunch", "expense", "18.50", today, "餐饮", "午餐"],
            ["demo-mixed-same", "expense", "18.50", today, "餐饮", "午餐"],
            ["demo-mixed-invalid", "expense", "abc", today, "交通", "金额异常"],
            ["demo-mixed-book", "expense", "36.00", today, "学习", "购买书籍"],
        ],
        "03-invalid.csv": [
            ["demo-invalid-amount", "expense", "abc", today, "餐饮", "金额异常"],
            ["demo-invalid-date", "expense", "12.00", "2026-13-40", "餐饮", "日期异常"],
        ],
    }
    adb = [args.adb, "-s", args.serial]
    destination = "/sdcard/Download/ImportExamples"
    subprocess.run(adb + ["shell", "mkdir", "-p", destination], check=True)
    with tempfile.TemporaryDirectory() as directory:
        for name, rows in examples.items():
            path = Path(directory) / name
            with path.open("w", encoding="utf-8-sig", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(["id", "type", "amount", "date", "category", "note"])
                writer.writerows(rows)
            subprocess.run(adb + ["push", str(path), f"{destination}/{name}"], check=True)
            subprocess.run(adb + ["shell", "am", "broadcast", "-a",
                                  "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d",
                                  f"file://{destination}/{name}"], check=True)
    print(f"Examples ready: {destination}")


if __name__ == "__main__":
    main()
