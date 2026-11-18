"""Place import preview examples in an emulator's Downloads; leaves app data intact."""
import argparse
import csv
from datetime import date
from pathlib import Path
import subprocess
import tempfile
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", default="emulator-5580")
    args = parser.parse_args()
    today = date.today().isoformat()
    batch = uuid.uuid4().hex[:12]
    normal = [
        ["demo-import-lunch", "expense", "18.50", today, "餐饮", "午餐"],
        ["demo-import-metro", "expense", "4.00", today, "交通", "地铁"],
        ["demo-import-work", "income", "200.00", today, "兼职", "助教劳务"],
        ["demo-import-book", "expense", "36.00", today, "学习", "购买书籍"],
        ["demo-import-dinner", "expense", "22.00", today, "餐饮", "晚餐"],
    ]
    examples = {
        "01-normal.csv": normal,
        "02-mixed.csv": [
            ["demo-mixed-lunch", "expense", "18.50", today, "餐饮", "午餐"],
            ["demo-mixed-lunch", "expense", "18.50", today, "餐饮", "午餐"],
            ["demo-mixed-same", "expense", "18.50", today, "餐饮", "午餐"],
            ["demo-mixed-invalid", "expense", "abc", today, "交通", "金额异常"],
            ["demo-mixed-book", "expense", "36.00", today, "学习", "购买书籍"],
            ["demo-mixed-metro", "expense", "4.00", today, "交通", "地铁"],
        ],
        "03-invalid.csv": [
            ["demo-invalid-amount", "expense", "abc", today, "餐饮", "金额异常"],
            ["demo-invalid-date", "expense", "12.00", "2026-13-40", "餐饮", "日期异常"],
        ],
        "04-times.csv": [
            ["demo-time-breakfast", "expense", "8.50", today, "餐饮", "早餐"],
            ["demo-time-class", "expense", "6.00", today, "交通", "去上课"],
            ["demo-time-income", "income", "300.00", today, "兼职", "劳务收入"],
        ],
        "05-duplicates.csv": [
            ["demo-duplicate-a", "expense", "12.00", today, "餐饮", "午餐"],
            ["demo-duplicate-a", "expense", "12.00", today, "餐饮", "午餐（重复）"],
            ["demo-duplicate-b", "expense", "12.00", today, "餐饮", "午餐（不同 ID）"],
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
                writer.writerow(["id", "type", "amount", "date", "category", "note", "customCategory", "transactionTime", "transactionZone"])
                writer.writerows([f"{batch}-{row[0]}"] + row[1:] + ["", ["12:35", "18:10", "09:00"][index % 3], "Asia/Shanghai"] for index, row in enumerate(rows))
            subprocess.run(adb + ["push", str(path), f"{destination}/{name}"], check=True)
            subprocess.run(adb + ["shell", "am", "broadcast", "-a",
                                  "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d",
                                  f"file://{destination}/{name}"], check=True)
    print(f"Examples ready: {destination}")


if __name__ == "__main__":
    main()
