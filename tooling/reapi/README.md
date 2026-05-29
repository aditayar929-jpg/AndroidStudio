# tooling/reapi

This directory must be populated by cloning upstream repository directly:

```bash
git clone https://github.com/bazelbuild/remote-apis tooling/reapi
```

In this environment, direct clone currently fails with:

- `CONNECT tunnel failed, response 403`

No hand-written proto placeholders are kept anymore.

After clone succeeds, use upstream proto and Java-related source/config files directly from this directory.
