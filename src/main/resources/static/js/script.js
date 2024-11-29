/**
 * @typedef {Object} AppInfo
 * @property {string} name - The name of the application
 * @property {string} command - Command of application
 */

/**
 * @typedef {Object} PollApp
 * @property {string} name
 * @property {string} command
 * @property {number} cpu_usage - Float point number
 * @property {number} io_time   - Float point number
 * @property {number} heap_size - Float point number
 */

/**
 * @typedef {Object} AppChart
 * @property {Chart} cpuUsage
 * @property {Chart} ioTime
 * @property {Chart} heapSize
 */

/** @type Map<number, AppInfo>  */
let apps = new Map();

/** @type Map<number, AppChart>  */
let charts = new Map();

switch (window.location.pathname) {
    case "/":
        showProfilePage();
        break;
    case "/run_app":
        showRunAppPage();
        break;
    case "/dashboard":
        loadApps();
        showAppsPage();
        break;
    default:
}

// NOTE: show body now to prevent jitters in display
document.body.style.display = "block";

function getCommand(id) {
    return document.querySelector(`[id^=cmd-${id}]`);
}

function getName(id) {
    return document.querySelector(`[id^=name-${id}]`);
}

function loadApps() {
    for (let el of document.querySelectorAll("[id^=app-]")) {
        let id = parseInt(/\d+/.exec(el.id)[0]);
        let name = /App: (.*)<\/div>/.exec(el.innerHTML)[1];
        let cmd = /Command: (.*)<\/div>/.exec(el.innerHTML)[1];
        apps.set(id, { name: name, command: cmd });
    }
}

/**
 *   Updates application Charts given an id of a live application.
 *   @param {number} id
 *   @param {PollApp} poll
 */
function updateCharts(id, poll) {
    let appCharts = charts.get(id);
    if (appCharts == null) {
        let canvas = [];
        let c = document.createElement("canvas");
        c.id = `cpu-usage-${id}`;
        canvas.push(c);

        c = document.createElement("canvas");
        c.id = `io-${id}`;
        canvas.push(c);

        c = document.createElement("canvas");
        c.id = `heap-size-${id}`;
        canvas.push(c);

        document.getElementById(`details-${id}`).innerHTML += newCanvasHTML(
            id,
            canvas
        );

        let cpuChart = newCpuUsageChart(
            document.getElementById(`cpu-usage-${id}`),
            [poll.cpu_usage]
        );
        let ioChart = newIOTimeChart(document.getElementById(`io-${id}`), [
            poll.io_time,
        ]);
        let heapChart = newHeapSizeChart(
            document.getElementById(`heap-size-${id}`),
            [poll.heap_size]
        );

        charts.set(id, {
            cpuUsage: cpuChart,
            ioTime: ioChart,
            heapSize: heapChart,
        });
    } else {
        // NOTE: check if the canvas is different. This can happen
        // due to DOM changes.
        let newCanvas = document.getElementById(`io-${id}`);
        if (appCharts.ioTime.canvas !== newCanvas) {
            let cpuChart = newCpuUsageChart(
                document.getElementById(`cpu-usage-${id}`),
                [...appCharts.cpuUsage.data.datasets[0].data, poll.cpu_usage]
            );
            let ioChart = newIOTimeChart(document.getElementById(`io-${id}`), [
                ...appCharts.ioTime.data.datasets[0].data,
                poll.io_time,
            ]);
            let heapChart = newHeapSizeChart(
                document.getElementById(`heap-size-${id}`),
                [...appCharts.heapSize.data.datasets[0].data, poll.heap_size]
            );
            charts.set(id, {
                cpuUsage: cpuChart,
                ioTime: ioChart,
                heapSize: heapChart,
            });
            return;
        }

        let len = appCharts.ioTime.data.labels?.length;
        appCharts.ioTime.data.labels?.push(len);
        appCharts.ioTime.data.datasets.forEach((el) =>
            el.data.push(poll.io_time)
        );
        appCharts.ioTime.update();

        len = appCharts.cpuUsage.data.labels?.length;
        appCharts.cpuUsage.data.labels?.push(len);
        appCharts.cpuUsage.data.datasets.forEach((el) =>
            el.data.push(poll.cpu_usage)
        );
        appCharts.cpuUsage.update();

        len = appCharts.heapSize.data.labels?.length;
        appCharts.heapSize.data.labels?.push(len);
        appCharts.heapSize.data.datasets.forEach((el) =>
            el.data.push(poll.heap_size)
        );
        appCharts.heapSize.update();
    }
}

function showAppsPage() {
    setInterval(pollApps, 1000);
    setInterval(getApps, 10_000);
}

async function getApps() {
    let r = await fetch(`/apps`);

    /** @type {Object<number, AppInfo>} */
    let j = await r.json();

    for (let [k, v] of /** @type [string, AppInfo][] */ (Object.entries(j))) {
        let id = parseInt(k);
        let info = apps.get(id);

        // App doesn't exist. Create new element.
        if (info === undefined) {
            apps.set(id, v);
            document.getElementById("apps").innerHTML += getNewAppHTML(id, v);
            continue;
        }

        // App has the same id, however has different values.
        if (info.name != v.name || info.command != v.command) {
            deleteApp(id);
            apps.set(id, v);
            document.getElementById("apps").innerHTML += getNewAppHTML(id, v);
        }
    }
}

async function pollApps() {
    let ids = Array.from(
        document.querySelectorAll("[id^=details-].collapse.show")
    ).map((el) => parseInt(/\d+/.exec(el.id)[0]));

    if (ids.length == 0) {
        // Delete charts not currently being displayed
        for (let [k, _] of charts) {
            deleteCharts(k);
        }
        return;
    }

    let r = await fetch(`/poll?ids=${ids.join(",")}`);
    let j = await r.json();

    let idsSet = new Set(ids);

    for (let [k, v] of /** @type {[string, PollApp|null][]} */ (
        Object.entries(j)
    )) {
        let id = parseInt(k);

        let info = apps.get(id);
        if (v === null || info === undefined) {
            deleteApp(id);
            continue;
        }

        // App with the same id, but different values. Delete current
        // and create another.
        if (v.name != info.name || v.command != info.command) {
            deleteApp(id);
            apps.set(id, { name: v.name, command: v.command });
            document.getElementById("apps").innerHTML += getNewAppHTML(id, v);
            continue;
        }

        updateCharts(id, v);
    }

    // Delete charts not currently being displayed
    for (let [k, v] of charts) {
        if (!idsSet.has(k)) {
            deleteCharts(k);
        }
    }
}

function showRunAppPage() {
    const formName = "form-run-app";
    let form = document.getElementById(formName);
    let heapSizeSelection = form["heapSizeSelection"];
    let heapSize = form["heapSize"];

    function handleHeapSizeDisplay() {
        if (heapSizeSelection.value == "Custom") {
            heapSize.style.display = "block";
            heapSize.labels[0].style.display = "block";
        } else {
            heapSize.style.display = "none";
            heapSize.labels[0].style.display = "none";
            heapSize.value = heapSizeSelection.value;
        }
    }

    form["jar"].addEventListener("change", () => handleCustomFileDisplay(form));
    form["heapSizeSelection"].addEventListener("change", handleHeapSizeDisplay);

    handleHeapSizeDisplay();
    handleCustomFileDisplay(form);
}

function showProfilePage() {
    let formName = "form-profile-app";
    let form = document.getElementById(formName);
    let automaticMode = form["automaticMode"];
    let tw_input = form["throughputWeight"];
    let pw_input = form["pauseTimeWeight"];
    let jar = form["jar"];

    function handleAutomaticModeDisplay() {
        if (!automaticMode.checked) {
            tw_input.type = "number";
            pw_input.type = "number";
            tw_input.labels[0].style.display = "block";
            pw_input.labels[0].style.display = "block";
            return;
        }
        // NOTE: changing input type first would not allow to access labels.
        tw_input.labels[0].style.display = "none";
        pw_input.labels[0].style.display = "none";
        tw_input.type = "hidden";
        pw_input.type = "hidden";
    }

    automaticMode.addEventListener("change", handleAutomaticModeDisplay);

    jar.addEventListener("change", () => handleCustomFileDisplay(form));

    tw_input.addEventListener("input", function () {
        let tw = tw_input.value;
        if (tw > 1 || tw < 0) return;
        pw_input.value = (1 - tw).toFixed(2);
    });

    pw_input.addEventListener("input", function () {
        let pw = pw_input.value;
        if (pw > 1 || pw < 0) return;
        tw_input.value = (1 - pw).toFixed(2);
    });

    handleCustomFileDisplay(form);
    handleAutomaticModeDisplay();
}

// handle profile request
function handleProfileAppResponse(event) {
    const target = event.detail.target;
    if (target.id !== "form-profile-app") {
        return;
    }
    const timePercentageCtx = document.getElementById("timePercentageChart");
    const cpuUsageCtx = document.getElementById("cpuUsageChart");

    if (timePercentageCtx == null || cpuUsageCtx == null) return;

    new Chart(timePercentageCtx, {
        type: "line",
        data: {
            labels: Array.from(
                { length: cpu_time.length },
                (_, index) => index
            ),
            datasets: [
                {
                    label: "IO Time Percentage",
                    data: io_time,
                    borderColor: "green",
                    fill: false,
                },
                {
                    label: "CPU Time Percentage",
                    data: cpu_time,
                    borderColor: "blue",
                    fill: false,
                },
            ],
        },
        options: {
            responsive: true,
            legend: { display: true },
        },
    });

    new Chart(cpuUsageCtx, {
        type: "line",
        data: {
            labels: Array.from(
                { length: cpu_usage.length },
                (_, index) => index
            ),
            datasets: [
                {
                    label: "CPU Usage Percentage",
                    data: cpu_usage,
                    borderColor: "red",
                    fill: false,
                },
            ],
        },
        options: {
            responsive: true,
            legend: { display: true },
        },
    });
    document.body.removeEventListener(
        "htmx:afterSwap",
        handleProfileAppResponse
    );
}

document.body.addEventListener("htmx:afterSwap", handleProfileAppResponse);

function handleCustomFileDisplay(form) {
    let jar = form["jar"];
    let file = form["file"];
    if (jar.value == "Custom") {
        file.style.display = "block";
        file.required = true;
        return;
    }
    file.required = false;
    file.style.display = "none";
}

/**
 * @param {number} id
 * @param {AppInfo} info
 * @returns {string}
 */
function getNewAppHTML(id, info) {
    return `<div id="app-${id}">
                <div class="row mb-3">
                    <div class="col">App: ${info.name}</div>
                    <div class="col align-self-end">
                        <button class="btn btn-primary" type="button" data-bs-toggle="collapse" data-bs-target="#details-${id}">
                            Show more details
                        </button>
                    </div>
                </div>

                <div class="collapse" id="details-${id}">
                     <div class="row mb-3"> 
                         <div class="col">ID: ${id}</div> 
                         <div class="col">Command: ${info.command}</div> 
                     </div> 
                </div>
            </div>`;
}

/**
 * @param {number} id
 * @param {HTMLCanvasElement[]} canvas
 * @returns {string}
 */
function newCanvasHTML(id, canvas) {
    return `<div id="canvas-${id}" class="row mb-3">
                <div class="col">
                    ${canvas[0].outerHTML}
                </div>
                <div class="col">
                    ${canvas[1].outerHTML}
                </div>
                <div class="col">
                    ${canvas[2].outerHTML}
                </div>
           </div>`;
}
/**
 *  Deletes every app info, including its charts.
 *  @param {number} id
 */
function deleteApp(id) {
    apps.delete(id);
    charts.delete(id);
    document.getElementById(`app-${id}`)?.remove();
}

/**
 *  @param {number} id
 */
function deleteCharts(id) {
    charts.delete(id);
    document.getElementById(`canvas-${id}`)?.remove();
}

/**
 * @param {HTMLCanvasElement} element
 * @param {number[]} data
 * @param {string} label
 */
function newChart(element, data, label) {
    return new Chart(element, {
        type: "line",
        data: {
            labels: Array.from({ length: data.length }, (_, index) => index),
            datasets: [
                {
                    label: label,
                    data: data,
                    borderColor: "red",
                    fill: false,
                },
            ],
        },
        options: {
            responsive: true,
            legend: { display: true },
        },
    });
}

/**
 * @param {HTMLCanvasElement} element
 * @param {number[]} data
 */
function newCpuUsageChart(element, data) {
    return newChart(element, data, "CPU Usage Percentage");
}

/**
 * @param {HTMLCanvasElement} element
 * @param {number[]} data
 */
function newIOTimeChart(element, data) {
    return newChart(element, data, "IO Time Percentage");
}

/**
 * @param {HTMLCanvasElement} element
 * @param {number[]} data
 */
function newHeapSizeChart(element, data) {
    return newChart(element, data, "Heap Size");
}
