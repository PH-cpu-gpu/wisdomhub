const PREFIX = 'wisdomhub:'
const memory = new Map<string, string>()

function keyOf(key: string): string {
  return key.startsWith(PREFIX) ? key : `${PREFIX}${key}`
}

function readRaw(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return memory.get(key) ?? null
  }
}

function writeRaw(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    memory.set(key, value)
  }
}

function removeRaw(key: string): void {
  try {
    window.localStorage.removeItem(key)
  } catch {
    // ignore
  }
  memory.delete(key)
}

export const storage = {
  get<T>(key: string, fallback: T): T {
    const raw = readRaw(keyOf(key))
    if (raw === null) return fallback
    try {
      return JSON.parse(raw) as T
    } catch {
      return fallback
    }
  },

  set<T>(key: string, value: T): void {
    const raw = JSON.stringify(value)
    if (raw !== undefined) writeRaw(keyOf(key), raw)
  },

  remove(key: string): void {
    removeRaw(keyOf(key))
  },

  has(key: string): boolean {
    return readRaw(keyOf(key)) !== null
  },

  clear(): void {
    try {
      const keys: string[] = []
      for (let index = 0; index < window.localStorage.length; index += 1) {
        const key = window.localStorage.key(index)
        if (key?.startsWith(PREFIX)) keys.push(key)
      }
      keys.forEach((key) => window.localStorage.removeItem(key))
    } catch {
      // ignore
    }

    for (const key of [...memory.keys()]) {
      if (key.startsWith(PREFIX)) memory.delete(key)
    }
  },
}

export function storageKey(name: string): string {
  return keyOf(name)
}
