package dev.bgcheck.app.shell;

import android.os.Bundle;

interface ICommandService {
    Bundle execute(in String[] args) = 0;
    void destroy() = 16777114;
}
