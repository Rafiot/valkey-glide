import argparse
import asyncio
import functools
import json
import random
import time
from datetime import datetime
from enum import Enum
from statistics import mean
import numpy as np
import redis.asyncio as redispy
from pybushka import (
    ClientConfiguration,
    RedisAsyncFFIClient,
    RedisAsyncSocketClient,
    LogLevel,
    set_logger_config,
)


class ChosenAction(Enum):
    GET_NON_EXISTING = 1
    GET_EXISTING = 2
    SET = 3


arguments_parser = argparse.ArgumentParser()
arguments_parser.add_argument(
    "--resultsFile",
    help="Where to write the results file",
    required=True,
)
arguments_parser.add_argument(
    "--dataSize",
    help="Size of data to set",
    required=True,
)
arguments_parser.add_argument(
    "--concurrentTasks",
    help="List of number of concurrent tasks to run",
    nargs="+",
    required=True,
)
arguments_parser.add_argument(
    "--clients",
    help="Which clients should run",
    required=True,
)
arguments_parser.add_argument(
    "--host",
    help="What host to target",
    required=True,
)
arguments_parser.add_argument(
    "--clientCount",
    help="Number of clients to run concurrently",
    nargs="+",
    required=True,
)
arguments_parser.add_argument(
    "--tls", help="Should benchmark a TLS server", action="store_true"
)
args = arguments_parser.parse_args()

PORT = 6379
PROB_GET = 0.8
PROB_GET_EXISTING_KEY = 0.8
SIZE_GET_KEYSPACE = 3750000  # 3.75 million
SIZE_SET_KEYSPACE = 3000000  # 3 million
started_tasks_counter = 0
running_tasks = set()
bench_json_results = []


def generate_value(size):
    return str("0" * size)


def generate_key_set():
    return str(random.randint(1, SIZE_SET_KEYSPACE + 1))


def generate_key_get():
    return str(random.randint(SIZE_SET_KEYSPACE, SIZE_GET_KEYSPACE + 1))


def choose_action():
    if random.random() > PROB_GET:
        return ChosenAction.SET
    if random.random() > PROB_GET_EXISTING_KEY:
        return ChosenAction.GET_NON_EXISTING
    return ChosenAction.GET_EXISTING


def calculate_latency(latency_list, percentile):
    return round(np.percentile(np.array(latency_list), percentile), 4)


def process_results():
    global bench_json_results
    global args

    # write json results to a file
    res_file_path = args.resultsFile
    with open(res_file_path, "w+") as f:
        json.dump(bench_json_results, f)


def timer(func):
    @functools.wraps(func)
    async def wrapper(*args, **kwargs):
        tic = time.perf_counter()
        await func(*args, **kwargs)
        toc = time.perf_counter()
        return toc - tic

    return wrapper


async def execute_commands(clients, total_commands, data_size, action_latencies):
    global started_tasks_counter
    while started_tasks_counter < total_commands:
        started_tasks_counter += 1
        chosen_action = choose_action()
        client = clients[started_tasks_counter % len(clients)]
        tic = time.perf_counter()
        if chosen_action == ChosenAction.GET_EXISTING:
            await client.get(generate_key_set())
        elif chosen_action == ChosenAction.GET_NON_EXISTING:
            await client.get(generate_key_get())
        elif chosen_action == ChosenAction.SET:
            await client.set(generate_key_set(), generate_value(data_size))
        toc = time.perf_counter()
        execution_time = toc - tic
        action_latencies[chosen_action].append(execution_time)
    return True


@timer
async def create_and_run_concurrent_tasks(
    clients, total_commands, num_of_concurrent_tasks, data_size, action_latencies
):
    global started_tasks_counter
    global get_latency
    global set_latency
    started_tasks_counter = 0
    for _ in range(num_of_concurrent_tasks):
        task = asyncio.create_task(
            execute_commands(clients, total_commands, data_size, action_latencies)
        )
        running_tasks.add(task)
        task.add_done_callback(running_tasks.discard)
    await asyncio.gather(*(list(running_tasks)))


def latency_results(prefix, latencies):
    result = {}
    result[prefix + "_p50_latency"] = calculate_latency(latencies, 50)
    result[prefix + "_p90_latency"] = calculate_latency(latencies, 90)
    result[prefix + "_p99_latency"] = calculate_latency(latencies, 9)
    result[prefix + "_average_latency"] = mean(latencies)
    result[prefix + "_std_dev"] = np.std(latencies)

    return result


async def create_clients(client_count, action):
    return [await action() for _ in range(client_count)]


async def run_clients(
    clients,
    client_name,
    event_loop_name,
    total_commands,
    num_of_concurrent_tasks,
    data_size,
):
    now = datetime.now().strftime("%H:%M:%S")
    print(
        f"Starting {client_name} data size: {data_size} concurrency: {num_of_concurrent_tasks} client count: {len(clients)} {now}"
    )
    action_latencies = {
        ChosenAction.GET_NON_EXISTING: list(),
        ChosenAction.GET_EXISTING: list(),
        ChosenAction.SET: list(),
    }
    time = await create_and_run_concurrent_tasks(
        clients, total_commands, num_of_concurrent_tasks, data_size, action_latencies
    )
    tps = int(started_tasks_counter / time)
    get_non_existing_latencies = action_latencies[ChosenAction.GET_NON_EXISTING]
    get_non_existing_latency_results = latency_results(
        "get_non_existing", get_non_existing_latencies
    )

    get_existing_latencies = action_latencies[ChosenAction.GET_EXISTING]
    get_existing_latency_results = latency_results(
        "get_existing", get_existing_latencies
    )

    set_latencies = action_latencies[ChosenAction.SET]
    set_results = latency_results("set", set_latencies)

    json_res = {
        **{
            "client": client_name,
            "loop": event_loop_name,
            "num_of_tasks": num_of_concurrent_tasks,
            "data_size": data_size,
            "tps": tps,
            "clientCount": len(clients),
        },
        **get_existing_latency_results,
        **get_non_existing_latency_results,
        **set_results,
    }

    bench_json_results.append(json_res)


async def main(
    event_loop_name,
    total_commands,
    num_of_concurrent_tasks,
    data_size,
    clients_to_run,
    host,
    client_count,
    use_tls,
):
    # Demo - Setting the internal logger to log every log that has a level of info and above, and save the logs to the first.log file.
    set_logger_config(LogLevel.INFO, "first.log")

    if clients_to_run == "all":
        clients = await create_clients(
            client_count,
            lambda: redispy.Redis(
                host=host, port=PORT, decode_responses=True, ssl=use_tls
            ),
        )

        await run_clients(
            clients,
            "redispy",
            event_loop_name,
            total_commands,
            num_of_concurrent_tasks,
            data_size,
        )

    if (
        clients_to_run == "all"
        or clients_to_run == "ffi"
        or clients_to_run == "babushka"
    ):
        # Babushka FFI
        config = ClientConfiguration(host=host, port=PORT, tls_enabled=use_tls)
        clients = await create_clients(
            client_count,
            lambda: RedisAsyncFFIClient.create(config),
        )
        await run_clients(
            clients,
            "babushka-FFI",
            event_loop_name,
            total_commands,
            num_of_concurrent_tasks,
            data_size,
        )

    if (
        clients_to_run == "all"
        or clients_to_run == "socket"
        or clients_to_run == "babushka"
    ):
        # Babushka Socket
        config = ClientConfiguration(host=host, port=PORT, tls_enabled=use_tls)
        clients = await create_clients(
            client_count,
            lambda: RedisAsyncSocketClient.create(config),
        )
        await run_clients(
            clients,
            "babushka-socket",
            event_loop_name,
            total_commands,
            num_of_concurrent_tasks,
            data_size,
        )


def number_of_iterations(num_of_concurrent_tasks):
    return max(100000, num_of_concurrent_tasks * 10000)


if __name__ == "__main__":
    concurrent_tasks = args.concurrentTasks
    data_size = int(args.dataSize)
    clients_to_run = args.clients
    client_count = args.clientCount
    host = args.host
    use_tls = args.tls

    product_of_arguments = [
        (data_size, int(num_of_concurrent_tasks), int(number_of_clients))
        for num_of_concurrent_tasks in concurrent_tasks
        for number_of_clients in client_count
        if int(number_of_clients) <= int(num_of_concurrent_tasks)
    ]

    for data_size, num_of_concurrent_tasks, number_of_clients in product_of_arguments:
        asyncio.run(
            main(
                "asyncio",
                number_of_iterations(num_of_concurrent_tasks),
                num_of_concurrent_tasks,
                data_size,
                clients_to_run,
                host,
                number_of_clients,
                use_tls,
            )
        )

    process_results()
