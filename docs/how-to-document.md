# How to Document

This guide provides detailed instructions for contributing to and maintaining the documentation for the Thunderbird for Android project. It explains the tools used, the structure of the documentation, and guidelines for creating and editing content.

We use [mdbook](https://rust-lang.github.io/mdBook/) to generate the documentation. The source files for the documentation are located in the `docs/` directory.

## Contributing

If you'd like to contribute to this project, please familiarize yourself with our [Contribution Guide](CONTRIBUTING.md).

To add or modify the documentation, please edit the markdown files located in the `docs/` directory using standard Markdown syntax, including [GitHub flavored Markdown](https://github.github.com/gfm/). You can use `headers`, `lists`, `links`, `code blocks`, and other Markdown features to structure your content.

For creating diagrams, we use the [mermaid](https://mermaid-js.github.io/mermaid/#/) syntax. To include mermaid diagrams in your Markdown files, use the following syntax:

````markdown
```mermaid
graph TD;
    A-->B;
    A-->C;
    B-->D;
    C-->D;
```
````

Result:

```mermaid
graph TD;
    A-->B;
    A-->C;
    B-->D;
    C-->D;
```

### Adding a New Page

To add a new page, create a markdown file in the `docs/` directory or within a suitable subfolder. For example:

- To create a new top-level page: `docs/new-page.md`.
- To create a page within a subfolder: `docs/subfolder/new-subpage.md`.

To include the new page in the table of contents, add a link to the `SUMMARY.md` file pointing to newly created page.

### Organizing with Subfolders

Subfolders in the `docs/` folder can be used to organize related documentation. This can be useful if related topics should be grouped together. For example, we have a subfolder named `architecture/` for all documentation related to our application's architecture.

### Linking New Pages in the Summary

The `SUMMARY.md` file serves as the table of contents (TOC) for the documentation. To include the new page in the TOC, a link needs to be added in the `SUMMARY.md` file, like so:

```markdown
- [Page Title](relative/path/to/file.md)
```

Indentation is used to create hierarchy in the TOC:

```markdown
- [Page Title](relative/path/to/file.md)
  - [Subpage Title](relative/path/to/subfolder/file.md)
```

## Documentation Toolchain

The documentation is built using mdbook and several extensions. Follow these steps to set up the required tools.

### Install mdbook and extensions

Ensure you have [Cargo](https://doc.rust-lang.org/cargo/) installed, then run:

```shell
./docs/install.sh
```

This script installs `mdbook` and the required extensions and other dependencies.

Use --force to update the dependencies, recommended when mdbook was updated:

```shell
./docs/install.sh --force
```

### Extensions

We use the following mdbook extensions:

- [mdbook-external-links](https://github.com/jonahgoldwastaken/mdbook-external-links) for opening external links in a new tab.
- [mdbook-last-changed](https://github.com/badboy/mdbook-last-changed) for last change date inclusion.
- [mdbook-mermaid](https://github.com/badboy/mdbook-mermaid) for diagram generation.
- [mdbook-pagetoc](https://github.com/slowsage/mdbook-pagetoc) for automatic page table of contents.

## Building the Documentation

Once you have `mdbook` and its extensions installed, you can build the documentation by running this command:

```shell
mdbook build docs
```

The generated documentation will be available in the `book/docs/latest/` folder.

To preview the documentation, run the following command:

```shell
mdbook serve docs --open
```

The `mdbook serve docs` command will serve the book at [http://localhost:3000](http://localhost:3000) and rebuild the documentation on changes. The `--open` option will open the book in your web browser and is optional.
