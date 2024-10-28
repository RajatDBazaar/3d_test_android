import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const myThreeDi = 'myThreeDiView';

class MyThreeDView extends StatefulWidget {
  const MyThreeDView({super.key});

  @override
  State<MyThreeDView> createState() => _MyThreeDViewState();
}

class _MyThreeDViewState extends State<MyThreeDView> {
  final Map<String, dynamic> params = <String, dynamic>{};

  @override
  void initState() {
    super.initState();
    params['fileNameWithExtension'] = 'boombox.glb';
    params['animationIndex'] = 1;
  }

  @override
  Widget build(BuildContext context) {
    return AndroidView(
      viewType: myThreeDi,
      layoutDirection: TextDirection.ltr,
      creationParams: params,
      creationParamsCodec: const StandardMessageCodec(),
    );
  }
}
