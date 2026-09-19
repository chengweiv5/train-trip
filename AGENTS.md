# AGENTS.md

## Git 推送

- 本项目推送代码时，目标始终为 `origin` 远端的 `main` 分支，使用显式命令 `git push origin HEAD:main`。
- 开发和提交仍在功能分支进行。推送前刷新 `origin/main`，确认可以快进；如远端已前进，先整合其提交并验证，不强制推送。
- 推送后回读 `origin/main`，确认包含本次提交。
