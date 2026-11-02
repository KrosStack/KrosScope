export interface Task {
  id: string;
  title: string;
}

export async function loadTasks(): Promise<Task[]> {
  const response = await fetch("/tasks");
  return response.json();
}

export function renderTasks(list: HTMLElement, tasks: Task[]): void {
  list.replaceChildren(...tasks.map((task) => {
    const item = document.createElement("li");
    item.textContent = task.title;
    return item;
  }));
}
