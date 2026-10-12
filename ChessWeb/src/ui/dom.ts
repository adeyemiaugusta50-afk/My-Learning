type Child = Node | string | null | false | undefined
type Props = Record<string, unknown>

/** Tiny DOM builder: h('button', { class: 'btn', onclick: fn }, 'Label'). `html` sets innerHTML; on* attach listeners. */
export function h<K extends keyof HTMLElementTagNameMap>(tag: K, props: Props = {}, ...children: Child[]): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag)
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue
    if (key === 'class') el.className = String(value)
    else if (key === 'html') el.innerHTML = String(value)
    else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2), value as EventListener)
    else if (value === true) el.setAttribute(key, '')
    else el.setAttribute(key, String(value))
  }
  for (const c of children) if (c !== null && c !== false && c !== undefined) el.append(c)
  return el
}
