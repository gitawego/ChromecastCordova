#!/usr/bin/env node

var regexp = /android.library.reference.([\d].*?)=(.*)/g;
var path = require('path');
var root = path.resolve(__dirname+'/../../../');
var fs = require('fs');
var exec = require('child_process').exec;

var mapping = {
	android:android
};
try{
	var depList = require(root+'/config/dependencies.json');
}catch(e){
	return console.error(e);
}

console.log("adding libs to project.properties");

Object.keys(depList).forEach(function(env){
	var dep = depList[env];
	mapping[env] && mapping[env](dep);
});

function android(dep){
	var projectSettingPath = root+'/platforms/android/project.properties';
	var setting = fs.readFileSync(projectSettingPath,'utf8'), lastIdx = 0, cmd = [];
	setting.replace(regexp,function(ignore,idx,libPath){
		idx = +idx;
		libPath = libPath.trim();
		lastIdx = idx > lastIdx?idx:lastIdx;
		console.log(idx,libPath);
		var tmpIdx = dep.indexOf(libPath);
		if(tmpIdx !== -1){
			dep.splice(dep.indexOf(libPath),1);
		}
		
	});
	console.log(dep,lastIdx);
	if(!dep.length){
		return;
	}
	dep.forEach(function(libPath,i){
		cmd.push("echo 'android.library.reference."+(lastIdx+i+1)+"="+libPath+"' >> "+projectSettingPath);
	});
	console.log(cmd);

	exec(cmd.join(' && '),function(){
		console.log(arguments);
	});

}

