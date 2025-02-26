#!/usr/bin/env python3

import argparse
import os
import requests
import yaml
import sys

from jinja2 import Template


def render_notes(
    version,
    versioncode,
    application,
    applicationid,
    longform_file,
    print_only=False,
    notesrepo="thunderbird/thunderbird-notes",
    notesbranch="master",
):
    """Render release notes from thunderbird-notes

    Update changelog files based on short release notes from thunderbird-notes.
    Render long-form notes to specified file,
    """
    tb_notes_filename = f"{version}.yml"
    tb_notes_directory = "android_release"
    if "0b" in version:
        tb_notes_filename = f"{version[0:-1]}eta.yml"
        tb_notes_directory = "android_beta"

    if application == "k9mail":
        build_type = "main"
    else:
        if applicationid == "net.thunderbird.android":
            build_type = "release"
        elif applicationid == "net.thunderbird.android.beta":
            build_type = "beta"
        else:
            # // throw error
            print("Error: Unsupported applicationid")
            sys.exit(1)

    if os.path.isdir(os.path.expanduser(notesrepo)):
        notes_path = os.path.join(
            os.path.expanduser(notesrepo), tb_notes_directory, tb_notes_filename
        )
        with open(notes_path) as fp:
            yaml_content = yaml.safe_load(fp.read())
    else:
        tb_notes_url = (
            os.path.join(
                f"https://api.github.com/repos/{notesrepo}/",
                f"contents/{tb_notes_directory}/{tb_notes_filename}?ref={notesbranch}",
            )
        )

        headers = {
            "Accept": "application/vnd.github.v3.raw"
        }

        response = requests.get(tb_notes_url, headers=headers)
        response.raise_for_status()
        yaml_content = yaml.safe_load(response.text)

    render_data = {"releases": {}}
    for release in reversed(yaml_content["release"]["releases"]):
        vers = release["version"]
        render_data["releases"][vers] = {}
        render_data["releases"][vers]["version"] = vers
        render_data["releases"][vers]["versioncode"] = int(versioncode)
        render_data["releases"][vers]["application"] = application
        render_data["releases"][vers]["date"] = release["release_date"]
        render_data["releases"][vers]["short_notes"] = []
        render_data["releases"][vers]["notes"] = {}
        for note in yaml_content["notes"]:
            if ("0b" not in version) or (
                "0b" in version and note["group"] == int(vers[-1])
            ):
                if (
                    note.get("thunderbird_only", False) and application == "k9mail"
                ) or (note.get("k9mail_only", False) and application == "thunderbird"):
                    continue
                if "note" in note:
                    tag = note["tag"].lower().capitalize()
                    if tag not in render_data["releases"][vers]["notes"]:
                        render_data["releases"][vers]["notes"][tag] = []
                    render_data["releases"][vers]["notes"][tag].append(
                        note["note"].strip()
                    )
                if "short_note" in note:
                    render_data["releases"][vers]["short_notes"].append(
                        note["short_note"].strip()
                    )

    render_files = {
        "changelog_master": {
            "template": "changelog_master.xml",
            "outfile": f"./app-{application}/src/{build_type}/res/raw/changelog_master.xml",
            "render_data": render_data["releases"][version],
        },
        "changelog": {
            "template": "changelog.txt",
            "outfile": f"./app-metadata/{applicationid}/en-US/changelogs/{versioncode}.txt",
            "render_data": render_data["releases"][version],
            "max_length": 500,
        },
        "changelog_long": {
            "template": "changelog_long.txt",
            "outfile": longform_file,
            "render_data": render_data["releases"][version],
        },
    }

    template_base = os.path.join(os.path.dirname(sys.argv[0]), "templates")

    for render_file in render_files:
        with open(os.path.join(template_base, render_files[render_file]["template"]), "r") as file:
            template = file.read()
        template = Template(template)
        rendered = template.render(render_files[render_file]["render_data"])
        if render_file == "changelog_master":
            if print_only:
                print(f"\n==={render_files[render_file]['outfile']}===")
                print("...")
                print(rendered)
                print("...")
            else:
                with open(render_files[render_file]["outfile"], "r") as file:
                    lines = file.readlines()
                    for index, line in enumerate(lines):
                        if "<changelog>" in line:
                            if version in lines[index + 1]:
                                break
                            lines.insert(index + 1, rendered)
                            break
                with open(render_files[render_file]["outfile"], "w") as file:
                    file.writelines(lines)
        elif render_file == "changelog" or render_file == "changelog_long":
            stripped = rendered.lstrip()
            maxlen = render_files[render_file].get("max_length", float("inf"))
            if len(stripped) > maxlen:
                print(
                    f"Error: Maximum length of {maxlen} exceeded, {render_file} is {len(stripped)} characters"
                )
                sys.exit(1)

            if print_only:
                print(f"\n==={render_files[render_file]['outfile']}===")
                print(stripped)
            else:
                with open(render_files[render_file]["outfile"], "x") as file:
                    file.write(stripped)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--print",
        "-p",
        action="store_true",
        help="Only print the processed release notes",
    )
    parser.add_argument(
        "--repository",
        "-r",
        default="thunderbird/thunderbird-notes",
        help="Repository or directory to retrieve thunderbird-notes from",
    )
    parser.add_argument(
        "--branch",
        "-b",
        default="master",
        help="Branch to retrieve thunderbird-notes from",
    )
    parser.add_argument(
        "applicationid",
        type=str,
        choices=[
            "net.thunderbird.android",
            "net.thunderbird.android.beta",
            "com.fsck.k9",
        ],
        help="thunderbird or k9mail",
    )
    parser.add_argument("version", type=str, help="Version name for this release")
    parser.add_argument(
        "versioncode",
        nargs="?",
        default="0",
        type=str,
        help="Version code for this release",
    )
    parser.add_argument(
        "longform_file",
        type=str,
        nargs="?",
        default="github_notes",
        help="File to render long-form notes to",
    )
    args = parser.parse_args()

    if args.applicationid == "com.fsck.k9":
        application = "k9mail"
    else:
        application = "thunderbird"

    render_notes(
        args.version,
        args.versioncode,
        application,
        args.applicationid,
        args.longform_file,
        print_only=args.print,
        notesrepo=args.repository,
        notesbranch=args.branch,
    )


if __name__ == "__main__":
    main()
