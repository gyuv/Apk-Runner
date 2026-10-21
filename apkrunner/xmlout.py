"""Render parsed binary XML elements back to readable text XML."""
from xml.sax.saxutils import quoteattr


def to_xml(elements):
    lines, stack = ['<?xml version="1.0" encoding="utf-8"?>'], []
    for i, (depth, tag, attrs) in enumerate(elements):
        while len(stack) > depth:
            lines.append("  " * (len(stack) - 1) + "</%s>" % stack.pop())
        a = "".join(" android:%s=%s" % (k, quoteattr(str(v).lower() if isinstance(v, bool) else str(v))) for k, v in attrs.items())
        has_child = i + 1 < len(elements) and elements[i + 1][0] > depth
        if has_child:
            lines.append("  " * depth + "<%s%s>" % (tag, a))
            stack.append(tag)
        else:
            lines.append("  " * depth + "<%s%s/>" % (tag, a))
    while stack:
        lines.append("  " * (len(stack) - 1) + "</%s>" % stack.pop())
    return "\n".join(lines) + "\n"
