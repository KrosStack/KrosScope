import { loadTasks, renderTasks } from "./tasks";

const list = document.getElementById("tasks");
if (list) {
  loadTasks().then((tasks) => renderTasks(list, tasks));
}
