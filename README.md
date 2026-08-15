# 🪓 WorldEdit (Minestom)

**WorldEdit** is our high-performance, native Minestom implementation of the popular building tool, powered by `worldedit-core`.

---

## 🎯 What does it do?
This library takes the pure mathematics and schematic parsing from `worldedit-core` and binds them directly into Minestom's world instance and command systems. It provides builders with the familiar `//` commands they expect, but executes them at speeds impossible on standard servers.

## 🏗️ Architecture

- **Command Bindings**: Hooks into [ACMD](../acmd) to provide `//set`, `//copy`, `//paste`, etc.
- **Block Setters**: Hooks into Minestom's optimized `Instance` batch-block-setting API to place millions of blocks in a fraction of a second.
- **History (Undo/Redo)**: Manages player action history, heavily optimized to avoid extreme memory usage during massive edits.

## 🚀 Key Features
- **Insanely Fast**: By leveraging Minestom's batch operations and [Polar's](../polar) memory model, edits that would normally crash a server finish instantly.
- **Familiar API**: Provides the exact same workflow builders are used to, requiring zero retraining.
- **Safe Execution**: Large edits are automatically chunked and throttled to ensure the server never stutters, even during multi-million block operations.
