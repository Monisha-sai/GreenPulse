import csv

input_file = "batch_task.csv"
output_file = "greenpulse_tasks.csv"

fieldnames = [
    "task_name",
    "instance_num",
    "job_name",
    "task_type",
    "status",
    "start_time",
    "end_time",
    "plan_cpu",
    "plan_mem"
]

MAX_TASKS = 5000

count = 0
skipped = 0

with open(input_file, "r", encoding="utf-8") as infile:

    reader = csv.DictReader(
        infile,
        fieldnames=fieldnames
    )

    with open(
        output_file,
        "w",
        newline="",
        encoding="utf-8"
    ) as outfile:

        writer = csv.writer(outfile)

        writer.writerow([
            "job_name",
            "task_name",
            "start_time",
            "end_time",
            "duration",
            "cpu_cores",
            "memory"
        ])

        for row in reader:

            try:

                start = float(row["start_time"])
                end = float(row["end_time"])

                plan_cpu = float(row["plan_cpu"])
                plan_mem = float(row["plan_mem"])

                duration = end - start

                # Ignore invalid tasks
                if duration <= 0:
                    skipped += 1
                    continue

                if plan_cpu <= 0:
                    skipped += 1
                    continue

                # Alibaba dataset:
                # 100 = 1 CPU core
                cpu_cores = plan_cpu / 100.0

                writer.writerow([
                    row["job_name"],
                    row["task_name"],
                    start,
                    end,
                    duration,
                    cpu_cores,
                    plan_mem
                ])

                count += 1

                if count >= MAX_TASKS:
                    break

            except (ValueError, KeyError):
                skipped += 1

print("GreenPulse preprocessing complete")
print("Valid tasks:", count)
print("Skipped rows:", skipped)
print("Output file:", output_file)