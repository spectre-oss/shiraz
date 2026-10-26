// SPDX-License-Identifier: GPL-3.0-only
// Included after the native launcher's filesystem and atomic-write helpers.
std::string desktopExec(const fs::path& path) {
    std::string quoted="\"";
    for(char c:path.string()) {
        if(c=='='||c==':'||c=='\n'||c=='\r')fail("Unsupported character in desktop executable path");
        if(c=='%')quoted+="%%";
        else {if(c=='\\'||c=='\"'||c=='`'||c=='$')quoted+='\\';quoted+=c;}
    }
    quoted+='\"';std::string result;
    for(char c:quoted){if(c=='\\')result+='\\';result+=c;}
    return result;
}
std::string desktopString(const std::string& value) {
    std::string result;
    for(char c:value) {
        switch(c) {
        case '\\':result+="\\\\";break;
        case '\n':result+="\\n";break;
        case '\r':result+="\\r";break;
        case '\t':result+="\\t";break;
        default:result+=c;
        }
    }
    return result;
}
void installDesktop(const fs::path& root,const std::map<std::string,std::string>& facts) {
    const fs::path data=xdg("XDG_DATA_HOME",".local/share");
    const std::string id=facts.at("package")+".Shiraz";
    const fs::path base=data/"shiraz/desktop"/facts.at("package")/artIsa;
    directory(base);
    int lock=open((base/".install.lock").c_str(),O_CREAT|O_RDWR|O_NOFOLLOW,0600);
    if(lock<0||flock(lock,LOCK_EX|LOCK_NB))fail("Another desktop installation is running");
    const fs::path target=base/(facts.at("version")+"-"+facts.at("sha256").substr(0,16));
    std::string command=desktopExec(target/"AppRun");
    std::string entry=readFile(root/"shiraz.desktop",65536),updated;
    std::size_t begin=0;
    while(begin<entry.size()) {
        auto end=entry.find('\n',begin);if(end==std::string::npos)end=entry.size();
        std::string line=entry.substr(begin,end-begin);begin=end+1;
        if(line.rfind("Exec=",0)==0)line="Exec="+command;
        // An explicit versioned artwork path avoids stale shell icon-theme caches
        // and refreshes the image when an APK/runtime is upgraded.
        if(line.rfind("Icon=",0)==0)line="Icon="+desktopString((target/"shiraz-icon.png").string());
        if(line.rfind("X-Purism-FormFactor=",0)==0)continue;
        updated+=line+"\n";
        // Declare both form factors even over SSH, where Phosh session variables
        // are absent. Desktop shells safely ignore this mobile metadata.
        if(line=="[Desktop Entry]")updated+="X-Purism-FormFactor=Workstation;Mobile;\n";
    }
    if(fs::is_symlink(target))fail("Installed runtime must not be a symlink");
    requireFile(root/"AppRun");requireFile(root/"shiraz-icon.png");
    if(!fs::exists(target)) {
        fs::path stage=base/(".install-"+std::to_string(getpid()));
        if(fs::exists(stage))fail("Installation staging path already exists");
        for(const auto& item:fs::recursive_directory_iterator(root))
            if(item.is_symlink()&&!within(fs::weakly_canonical(item.path()),root))fail("Packaged symlink escapes AppDir");
        directory(stage);
        try {
            for(const auto& item:fs::recursive_directory_iterator(root)) {
                const fs::path destination=stage/item.path().lexically_relative(root);
                if(item.is_symlink())fs::copy_symlink(item.path(),destination);
                else if(item.is_directory())directory(destination);
                else if(item.is_regular_file())fs::copy_file(item.path(),destination);
                else fail("Unsupported file type in AppDir");
            }
            fs::rename(stage,target);
        } catch(...) {fs::remove_all(stage);throw;}

    }
    requireFile(target/"AppRun");
    const fs::path icons=data/"icons/hicolor/256x256/apps",applications=data/"applications";
    directory(icons);directory(applications);
    // Icon bytes are binary; copy to an exclusive temporary path before replacement.
    fs::path icon=icons/(id+".png"),temp=icons/(id+".tmp."+std::to_string(getpid()));
    fs::copy_file(target/"shiraz-icon.png",temp);
    fs::rename(temp,icon);
    atomicWrite(applications/(id+".desktop"),updated);
    // Migrate the two pre-installer launchers only when their contents identify
    // the matching Shiraz AppImage. Preserve unrelated/custom launchers.
    std::string legacy,product;
    if(facts.at("package")=="chat.delta"){legacy="shiraz-messenger";product="Messenger-Shiraz-";}
    if(facts.at("package")=="net.osmand.plus"){legacy="shiraz-osmand";product="OsmAnd-Shiraz-";}
    if(!legacy.empty()) {
        fs::path old=applications/(legacy+".desktop");
        if(fs::is_regular_file(old)&&!fs::is_symlink(old)) {
            std::string text=readFile(old,65536);
            if(text.find("X-AppImage-Version=")!=std::string::npos&&text.find(product)!=std::string::npos) {
                fs::path archive=data/"shiraz/legacy-launchers";directory(archive);
                fs::rename(old,archive/(legacy+".desktop."+std::to_string(getpid())));
            }
        }
    }
    close(lock);
    std::cout<<"Installed "<<facts.at("package")<<"\nLauncher: "<<(applications/(id+".desktop"))<<"\nRuntime: "<<target<<"\n";
}
