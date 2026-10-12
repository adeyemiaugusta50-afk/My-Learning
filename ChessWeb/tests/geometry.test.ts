import { describe, expect, it } from 'vitest'
import { cellOf, fileLabels, rankLabels, squareAt, squareAtPoint } from '../src/logic/geometry'

describe('board geometry (touch/click mapping)', () => {
  it('normal orientation: white at the bottom, a1 bottom-left', () => {
    expect(squareAt(0, 0, true)).toBe('a8')
    expect(squareAt(0, 7, true)).toBe('h8')
    expect(squareAt(7, 0, true)).toBe('a1')
    expect(squareAt(7, 7, true)).toBe('h1')
    expect(squareAt(6, 4, true)).toBe('e2')
  })
  it('rotated board is exactly a 180 degree turn', () => {
    expect(squareAt(0, 0, false)).toBe('h1')
    expect(squareAt(0, 7, false)).toBe('a1')
    expect(squareAt(7, 0, false)).toBe('h8')
    expect(squareAt(7, 7, false)).toBe('a8')
    for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) expect(squareAt(r, c, true)).toBe(squareAt(7 - r, 7 - c, false))
  })
  it('cell and square mappings are inverses in both orientations', () => {
    for (const o of [true, false]) {
      const seen = new Set<string>()
      for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) {
        const sq = squareAt(r, c, o)
        seen.add(sq)
        expect(cellOf(sq, o)).toEqual({ row: r, col: c })
      }
      expect(seen.size).toBe(64)
    }
  })
  it('pixel touch points map to the same squares as cells', () => {
    for (const o of [true, false]) for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) {
      const expected = squareAt(r, c, o)
      for (const off of [1, 50, 99.9]) expect(squareAtPoint(c * 100 + off, r * 100 + off, 800, o)).toBe(expected)
    }
  })
  it('points outside the board are ignored; sizes scale', () => {
    expect(squareAtPoint(-1, 10, 800, true)).toBeNull()
    expect(squareAtPoint(10, -0.1, 800, true)).toBeNull()
    expect(squareAtPoint(800, 10, 800, true)).toBeNull()
    expect(squareAtPoint(10, 10, 0, true)).toBeNull()
    expect(squareAtPoint(10, 10, 360, false)).toBe('h1')
    expect(squareAtPoint(10, 10, 360, true)).toBe('a8')
    expect(squareAtPoint(10, 350, 360, true)).toBe('a1')
  })
  it('coordinate labels follow the orientation', () => {
    expect(fileLabels(true).join('')).toBe('abcdefgh')
    expect(fileLabels(false).join('')).toBe('hgfedcba')
    expect(rankLabels(true).join('')).toBe('87654321')
    expect(rankLabels(false).join('')).toBe('12345678')
  })
  it('rejects off-board input', () => {
    expect(() => squareAt(8, 0, true)).toThrow()
    expect(() => squareAt(0, -1, false)).toThrow()
    expect(() => cellOf('i9', true)).toThrow()
  })
})
